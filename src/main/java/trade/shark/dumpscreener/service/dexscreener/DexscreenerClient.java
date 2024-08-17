package trade.shark.dumpscreener.service.dexscreener;

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.RateLimiter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import trade.shark.dumpscreener.domain.NetworkContract;
import trade.shark.dumpscreener.enums.Network;
import trade.shark.dumpscreener.service.MetadataService;
import trade.shark.dumpscreener.service.PriceProvider;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Primary
@Slf4j
public class DexscreenerClient implements PriceProvider {
  public static final int DEXSCREENER_TOKEN_COUNT_THRESHOLD = 25;
  private static final double REQUESTS_PER_SECOND = 4.5;
  private final ForkJoinPool forkJoinPool;
  private final RestTemplate restTemplate;
  private final RateLimiter rateLimiter;
  private final MetadataService metadataService;

  public DexscreenerClient(@Qualifier("dexScreenerThreadPool") ForkJoinPool forkJoinPool, RestTemplate restTemplate, @Lazy MetadataService metadataService) {
    this.forkJoinPool = forkJoinPool;
    this.restTemplate = restTemplate;
    this.metadataService = metadataService;
    this.rateLimiter = RateLimiter.create(REQUESTS_PER_SECOND);
  }

  @SneakyThrows
  @Override
  public Map<NetworkContract, BigDecimal> loadPrices(List<NetworkContract> tokenContract) {
    metadataService.getTokens();
    long start = System.currentTimeMillis();
    final Map<NetworkContract, NetworkContract> lpContractToTokenContractMap = tokenContract.stream()
        .map(metadataService::getTokenByContract)
        .distinct()
        .flatMap(t -> t.getContracts().stream().map(contract -> Map.entry(t.getDexLiquidityPool().getContract(), contract)))
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (existing, replacement) -> existing));

    final Map<NetworkContract, PoolMetadata> poolsMetadata = loadLiquidityPoolsData(lpContractToTokenContractMap.keySet());
    final Map<NetworkContract, BigDecimal> prices = poolsMetadata.entrySet().stream().collect(Collectors.toMap(e -> lpContractToTokenContractMap.get(e.getKey()), entry -> entry.getValue().getPriceUsd()));
    log.debug("loadPrices took {} ms. Size={}", System.currentTimeMillis() - start, prices.values().stream().filter(Objects::nonNull).count());
    return prices;
  }

  @SneakyThrows
  public Map<NetworkContract, PoolMetadata> loadLiquidityPoolsData(Collection<NetworkContract> pools) {
    final Map<NetworkContract, PoolMetadata> resultMap = new HashMap<>();
    final Map<Network, List<NetworkContract>> poolsByNetwork = pools.stream()
        .collect(Collectors.groupingBy(NetworkContract::getNetwork));
    for (Network network : poolsByNetwork.keySet()) {
      final List<NetworkContract> netContracts = poolsByNetwork.get(network);
      final Map<String, NetworkContract> poolAddressContractMap = netContracts.stream()
          .collect(Collectors.toMap(nc -> nc.getContractAddress().toUpperCase(), Function.identity(), (existing, replacement) -> replacement));
      final List<String> addressesList = poolAddressContractMap.keySet().stream().toList();
      final Map<NetworkContract, PoolMetadata> responseMap = forkJoinPool.submit(() ->
          Lists.partition(addressesList, DEXSCREENER_TOKEN_COUNT_THRESHOLD).parallelStream()
              .map(addressList -> getPoolData(network, addressList))
              .filter(Objects::nonNull)
              .filter(response -> response.getPairs() != null)
              .flatMap(response -> response.getPairs().stream())
              .filter(poolMetadata -> poolAddressContractMap.containsKey(poolMetadata.getPairAddress().toUpperCase()))
              .collect(Collectors.toMap(md -> poolAddressContractMap.get(md.getPairAddress().toUpperCase()), Function.identity()))
      ).get();
      resultMap.putAll(responseMap);
    }
    return resultMap;
  }

  @SneakyThrows
  public Map<NetworkContract, List<PoolMetadata>> loadTokenPools(Collection<NetworkContract> tokenContracts) {
    final Map<String, NetworkContract> addressContractMap = tokenContracts.stream()
        .collect(Collectors.toMap(c -> c.getContractAddress().toUpperCase(), Function.identity(), (existing, replacement) -> replacement));
    final List<String> addresses = addressContractMap.keySet().stream().toList();
    return forkJoinPool.submit(() -> {
      return Lists.partition(addresses, DEXSCREENER_TOKEN_COUNT_THRESHOLD).parallelStream()
          .map(this::getTokenMetadata)
          .filter(Objects::nonNull)
          .filter(response -> response.getPairs() != null)
          .flatMap(response -> response.getPairs().stream())
          .filter(poolMetadata -> addressContractMap.containsKey(poolMetadata.getBaseToken().getAddress().toUpperCase()))
          .collect(Collectors.groupingBy(poolMetadata -> addressContractMap.get(poolMetadata.getBaseToken().getAddress().toUpperCase())));
    }).get();
  }

  private TokensResponse getTokenMetadata(Collection<String> contracts) {
    try {
      this.rateLimiter.acquire();
      final String url = "https://api.dexscreener.com/latest/dex/tokens/" + String.join(",", contracts);
      final ResponseEntity<TokensResponse> responseEntity = restTemplate.getForEntity(
          url, TokensResponse.class);
      final TokensResponse body = responseEntity.getBody();
      return body;
    } catch (Exception exception) {
      log.error("Exception loading contracts: {}", contracts, exception);
      return null;
    }
  }

  private TokensResponse getPoolData(Network network, Collection<String> contracts) {
    try {
      this.rateLimiter.acquire();
      final String url = "https://api.dexscreener.com/latest/dex/pairs/" + network.getDexScreenerName() + "/" + String.join(",", contracts);
      final ResponseEntity<TokensResponse> responseEntity = restTemplate.getForEntity(
          url, TokensResponse.class);
      final TokensResponse body = responseEntity.getBody();
      return body;
    } catch (Exception exception) {
      log.error("Exception loading contracts: {}", contracts, exception);
      return null;
    }
  }

  @NotNull
  public Map<NetworkContract, PoolMetadata> leaveOnlyBiggestPoolPerContract(Map<NetworkContract, List<PoolMetadata>> poolsMetadata) {
    final Map<NetworkContract, PoolMetadata> poolMetadataMap = new HashMap<>();
    poolsMetadata.forEach((contract, metadataList) -> {
      metadataList.stream()
          .filter(pool -> pool.getLiquidity() != null && pool.getLiquidity().getUsd() != null && pool.getLiquidity().getUsd().compareTo(BigDecimal.ZERO) > 0)
          .max(Comparator.comparing(pool -> pool.getLiquidity().getUsd()))
          .ifPresent(value -> poolMetadataMap.put(contract, value));
    });
    return poolMetadataMap;
  }
}
