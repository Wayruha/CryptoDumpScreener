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
import trade.shark.dumpscreener.domain.Token;
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
  private static final int REQUESTS_PER_SECOND = 4;
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
  public Map<NetworkContract, PoolMetadata> loadPoolMetadata(Collection<NetworkContract> networkContracts) {
    final Map<NetworkContract, List<PoolMetadata>> allPools = loadLiquidityPools(networkContracts);
    return filterPoolsByMaxLiquidity(allPools);
  }

  @SneakyThrows
  @Override
  public Map<NetworkContract, BigDecimal> loadPrices(List<NetworkContract> networkContracts) {
    long start = System.currentTimeMillis();
    final Map<NetworkContract, List<PoolMetadata>> poolsMetadata = loadLiquidityPools(networkContracts);
    final Map<NetworkContract, PoolMetadata> poolMetadataMap = filterPoolsByPairAddress(poolsMetadata);
    final Map<NetworkContract, BigDecimal> prices = poolMetadataMap.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().getPriceUsd()));
    log.debug("loadPrices took {} ms. Size={}", System.currentTimeMillis() - start, prices.values().stream().filter(Objects::nonNull).count());
    return prices;
  }

  @SneakyThrows
  private Map<NetworkContract, List<PoolMetadata>> loadLiquidityPools(Collection<NetworkContract> networkContracts) {
    final Map<String, NetworkContract> addressContractMap = networkContracts.stream()
        .collect(Collectors.toMap(NetworkContract::getContractAddress, Function.identity()));
    final List<String> addresses = addressContractMap.keySet().stream().toList();
    return forkJoinPool.submit(() -> {
      return Lists.partition(addresses, DEXSCREENER_TOKEN_COUNT_THRESHOLD).parallelStream()
          .map(this::getMetadata)
          .flatMap(response -> response.getPairs().stream())
          .filter(poolMetadata -> addressContractMap.containsKey(poolMetadata.getBaseToken().getAddress().toUpperCase()))
          .collect(Collectors.groupingBy(poolMetadata -> addressContractMap.get(poolMetadata.getBaseToken().getAddress().toUpperCase())));
    }).get();
  }

  @NotNull
  private Map<NetworkContract, PoolMetadata> filterPoolsByPairAddress(Map<NetworkContract, List<PoolMetadata>> poolsMetadata) {
    final Map<NetworkContract, PoolMetadata> poolMetadataMap = new HashMap<>();
    poolsMetadata.forEach((contract, metadataList) -> {
      final Token token = metadataService.getTokenByContract(contract);
      metadataList.stream()
          .filter(md -> token.getDexLiquidityPool().getLiquidityPairAddress().equalsIgnoreCase(md.getPairAddress()))
          .findFirst()
          .ifPresent(value -> poolMetadataMap.put(contract, value));
    });
    return poolMetadataMap;
  }

  @NotNull
  private Map<NetworkContract, PoolMetadata> filterPoolsByMaxLiquidity(Map<NetworkContract, List<PoolMetadata>> poolsMetadata) {
    final Map<NetworkContract, PoolMetadata> poolMetadataMap = new HashMap<>();
    poolsMetadata.forEach((contract, metadataList) -> {
      metadataList.stream()
          .filter(pool -> pool.getLiquidity() != null && pool.getLiquidity().getUsd() != null && pool.getLiquidity().getUsd().compareTo(BigDecimal.ZERO) > 0)
          .max(Comparator.comparing(pool -> pool.getLiquidity().getUsd()))
          .ifPresent(value -> poolMetadataMap.put(contract, value));
    });
    return poolMetadataMap;
  }

  private TokensResponse getMetadata(Collection<String> contracts) {
    try {
      this.rateLimiter.acquire();
      final ResponseEntity<TokensResponse> responseEntity = restTemplate.getForEntity(
          "https://api.dexscreener.com/latest/dex/tokens/" + String.join(",", contracts), TokensResponse.class);
      final TokensResponse body = responseEntity.getBody();
      return body;
    } catch (Exception exception) {
      throw new DexScreenerClientException("Exception loading contracts: " + contracts, exception);
    }
  }
}
