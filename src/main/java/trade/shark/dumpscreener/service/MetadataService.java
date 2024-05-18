package trade.shark.dumpscreener.service;

import com.litesoftwares.coingecko.CoinGeckoApiClient;
import com.litesoftwares.coingecko.domain.Coins.CoinList;
import com.litesoftwares.coingecko.domain.Coins.CoinPriceData;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import trade.shark.dumpscreener.config.AppProperties;
import trade.shark.dumpscreener.domain.NetworkContract;
import trade.shark.dumpscreener.domain.Token;
import trade.shark.dumpscreener.domain.TradePair;
import trade.shark.dumpscreener.enums.Network;
import trade.shark.dumpscreener.event.MetadataRefreshedEvent;
import trade.wayruha.cryptocompare.domain.AssetSortBy;
import trade.wayruha.cryptocompare.request.PageRequest;
import trade.wayruha.cryptocompare.response.AssetData;
import trade.wayruha.cryptocompare.response.ExchangeData;
import trade.wayruha.cryptocompare.response.InstrumentData;
import trade.wayruha.cryptocompare.response.InstrumentMapping;
import trade.wayruha.cryptocompare.service.AssetDataService;
import trade.wayruha.cryptocompare.service.SpotDataService;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

//todo it should hold the metadata returned by aggregator API (coingecko?)
@Service
@Slf4j
@RequiredArgsConstructor
public class MetadataService {
  private static final int CG_REQUEST_LENGTH_THRESHOLD = 5000;

  private final AppProperties properties;
  private final CoinGeckoApiClient coinGeckoApiClient;
  private final AssetDataService assetDataService;
  private final SpotDataService spotDataService;
  private final ExecutorService executorService;
  private final ApplicationEventPublisher eventPublisher;
  private final List<Token> coinsData = new ArrayList<>();
  @Getter
  private LocalDateTime lastUpdate;
  //duplication of data for easy access
  private final Map<NetworkContract, Token> duplicateCoinsMap = new HashMap<>();
  private final Map<Network, List<NetworkContract>> contractsByNetworkMap = new HashMap<>();

  public void fetchCoinsMetadata() throws ExecutionException, InterruptedException {
    final LocalDateTime start = LocalDateTime.now();
    log.debug("Refreshing cache");
    final Future<List<CoinList>> cgCoinsListFuture = executorService.submit(coinGeckoApiClient::getCoinList);
    final Future<List<AssetData>> ccMetadataFuture = executorService.submit(() ->
        assetDataService.iterativelyLoadTopList(AssetSortBy.CIRCULATING_MKT_CAP_USD, PageRequest.unpaged()));
    final List<CoinList> coins = cgCoinsListFuture.get();
    final Map<String, CoinPriceData> cgMetadata = getCgMetadata(coins); //todo sublist for quicker development
    final List<Token> chainSupportedTokens = buildTokenData(coins);
    final List<Token> filteredTokens = filterTokens(chainSupportedTokens, cgMetadata);

    log.debug("Tokens Filtered: {}. Existing on chains: {}. Supported by CoinGecko: {}.", filteredTokens.size(), coins.size(), chainSupportedTokens.size());
    populateCryptoCompareIds(filteredTokens, ccMetadataFuture.get());
    populateTradePair(filteredTokens);

    updateMetadata(filteredTokens);
    eventPublisher.publishEvent(new MetadataRefreshedEvent(filteredTokens, Duration.between(start, lastUpdate)));
    log.debug("Cache updated!");
  }

  public Token getTokenByContract(NetworkContract contract) {
    if (contract == null) return null;
    return duplicateCoinsMap.get(contract);
  }

  public List<NetworkContract> getContractsByNetwork(Network network) {
    return contractsByNetworkMap.get(network);
  }

  private void updateMetadata(List<Token> filteredTokens) {
    this.lastUpdate = LocalDateTime.now();
    this.coinsData.clear();
    this.coinsData.addAll(filteredTokens);
    this.duplicateCoinsMap.clear();
    filteredTokens.forEach(token ->
        token.getContracts().forEach(contract -> this.duplicateCoinsMap.put(contract, token))
    );
    this.contractsByNetworkMap.clear();
    final Map<Network, List<NetworkContract>> contractsByNetwork = filteredTokens.stream().flatMap(token -> token.getContracts().stream()).collect(Collectors.groupingBy(NetworkContract::getNetwork));
    this.contractsByNetworkMap.putAll(contractsByNetwork);
  }

  @NotNull
  private List<Token> buildTokenData(List<CoinList> coins) {
    final List<Token> tokens = coins.stream()
        .map(coin -> {
          final Map<String, String> platforms = coin.getPlatforms();
          final List<NetworkContract> contracts = platforms.keySet().stream()
              .map(Network::getByCgName)
              .filter(Objects::nonNull)
              .filter(net -> properties.getNetworks().contains(net))
              .map(net -> NetworkContract.of(platforms.get(net.getCgName()), net))
              .toList();
          return contracts.isEmpty() ? null :
              Token.builder()
                  .cgId(coin.getId())
                  .cgSymbol(coin.getSymbol())
                  .name(coin.getName())
                  .tradePairs(new HashMap<>())
                  .contracts(contracts)
                  .build();
        })
        .filter(Objects::nonNull)
        .toList();
    return tokens;
  }

  private Map<String, CoinPriceData> getCgMetadata(List<CoinList> coins) throws InterruptedException {
    long start = System.currentTimeMillis();
    final Map<String, CoinPriceData> metadata = new HashMap<>();
    StringBuilder sublistBuilder = new StringBuilder();
    List<String> sublist = new ArrayList<>();

    for (CoinList coin : coins) {
      final String coinId = coin.getId();
      final int coinIdLength = coinId.length();

      if (sublistBuilder.length() + coinIdLength + 1 > CG_REQUEST_LENGTH_THRESHOLD) {
        metadata.putAll(fetchCoinGeckoMetadata(sublist));
        sublistBuilder = new StringBuilder();
        sublist = new ArrayList<>();
      }

      if (!sublistBuilder.isEmpty()) {
        sublistBuilder.append(",");
      }
      sublistBuilder.append(coinId);
      sublist.add(coinId);
    }

    if (!sublistBuilder.isEmpty()) {
      metadata.putAll(fetchCoinGeckoMetadata(sublist));
    }
    log.debug("Fetched CoinGecko metadata: {} items, {}ms.", metadata.size(), System.currentTimeMillis() - start);
    return metadata;
  }

  private Map<String, CoinPriceData> fetchCoinGeckoMetadata(List<String> sublist) throws InterruptedException {
    log.debug("fetching partial cg metadata: {}", sublist.size());
    Thread.sleep(1000); //todo why?
    return coinGeckoApiClient.getCoinPriceData(sublist);
  }

  private List<Token> filterTokens(List<Token> tokens, Map<String, CoinPriceData> cgMetadata) {
    long start = System.currentTimeMillis();

    final Set<String> filteredTokenIds = cgMetadata.entrySet().stream()
        .filter(Objects::nonNull)
        .filter(e -> properties.getVolume24h() == null ||
            (e.getValue().getUsdVolume24H() != null && e.getValue().getUsdVolume24H().compareTo(properties.getVolume24h()) > 0))
        .filter(e -> properties.getMarketCap() == null
            || (e.getValue().getMarketCap() != null && e.getValue().getMarketCap().compareTo(properties.getMarketCap()) > 0))
        .map(Map.Entry::getKey)
        .collect(Collectors.toSet());

    final List<Token> filteredTokens = tokens.stream().filter(t -> filteredTokenIds.contains(t.getCgId())).toList();
    log.debug("filtered tokens: {} items, {}ms", filteredTokens.size(), System.currentTimeMillis() - start);
    return filteredTokens;
  }

  private void populateTradePair(List<Token> tokens) {
    long start = System.currentTimeMillis();
    properties.getCexes().forEach(cex -> {
      final Map<String, ExchangeData> availableMarkets = spotDataService.getAvailableMarkets(cex.getCcName(), List.of());
      if (!availableMarkets.containsKey(cex.getCcName())) return;
      final ExchangeData data = availableMarkets.get(cex.getCcName());
      final List<InstrumentMapping> instruments = data.getInstruments().values().stream().map(InstrumentData::getInstrumentMapping).toList();
      instruments.stream()
          .filter(instrument -> properties.getStableCoins().stream().anyMatch(stableCoin -> stableCoin.equalsIgnoreCase(instrument.getQuote())))
          .collect(Collectors.groupingBy(InstrumentMapping::getBase))
          .values()
          .stream()
          .map(dupes -> dupes.stream().findFirst())
          .filter(Optional::isPresent)
          .map(Optional::get)
          .forEach(instrument -> {
            tokens.stream()
                .filter(token -> instrument.getBase().equalsIgnoreCase(token.getCcSymbol()))
                .forEach(token -> {
                  token.getTradePairs().put(cex, new TradePair(instrument.getBase(), instrument.getQuote()));
                });
          });
    });
    log.debug("Fetched USD trade pair for every CEX: {} items, {}ms", tokens.size(), System.currentTimeMillis() - start);
    final long count = tokens.stream().filter(t -> t.getCcSymbol() == null).count();
    log.debug("Tokens without cryptoCompareSymbol: {}", count);
  }

  private static void populateCryptoCompareIds(List<Token> tokens, List<AssetData> metadata) {
    long start = System.currentTimeMillis();
    final Map<NetworkContract, AssetData> assetDataMap = metadata.stream()
        .flatMap(assetData -> assetData.getSupportedPlatforms().stream()
            .filter(platform -> Network.getByCcName(platform.getBlockchain()) != null)
            .filter(platform -> platform.getSmartContractAddress() != null)
            .map(platform -> NetworkContract.of(platform.getSmartContractAddress(), Network.getByCcName(platform.getBlockchain())))
            .map(contract -> new AbstractMap.SimpleEntry<>(contract, assetData)))
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (oldValue, newValue) -> oldValue));

    tokens.forEach(token -> {
      token.getContracts().stream()
          .map(assetDataMap::get)
          .filter(Objects::nonNull)
          .findFirst()
          .ifPresent(assetData -> {
            token.setDeploymentTime(assetData.getLaunchDate());
            token.setCcId(assetData.getName());
            token.setCcSymbol(assetData.getSymbol());
          });
    });
    log.debug("Supported by CryptoCompare: {} items, {}ms", tokens.size(), System.currentTimeMillis() - start);
  }

  public List<Token> getTokens() {
    return new ArrayList<>(coinsData);
  }
}
