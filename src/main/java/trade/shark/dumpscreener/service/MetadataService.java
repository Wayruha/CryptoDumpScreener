package trade.shark.dumpscreener.service;

import com.litesoftwares.coingecko.CoinGeckoApiClient;
import com.litesoftwares.coingecko.domain.Coins.CoinList;
import com.litesoftwares.coingecko.domain.Coins.CoinPriceData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import trade.shark.dumpscreener.config.AppProperties;
import trade.shark.dumpscreener.domain.NetworkContract;
import trade.shark.dumpscreener.domain.Token;
import trade.shark.dumpscreener.domain.TradePair;
import trade.shark.dumpscreener.enums.Network;
import trade.wayruha.cryptocompare.domain.AssetSortBy;
import trade.wayruha.cryptocompare.request.PageRequest;
import trade.wayruha.cryptocompare.response.AssetData;
import trade.wayruha.cryptocompare.response.ExchangeData;
import trade.wayruha.cryptocompare.response.InstrumentData;
import trade.wayruha.cryptocompare.response.InstrumentMapping;
import trade.wayruha.cryptocompare.service.AssetDataService;
import trade.wayruha.cryptocompare.service.SpotDataService;

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

  private static final List<Token> coinsData = new ArrayList<>();
  private final AppProperties properties;
  private final CoinGeckoApiClient coinGeckoApiClient;
  private final AssetDataService assetDataService;
  private final SpotDataService spotDataService;
  private final ExecutorService executorService;

  public Token resolveTokenByContract(NetworkContract contract) {
    return null;
  }

  public void fetchCoinsMetadata() throws ExecutionException, InterruptedException {
    log.debug("refreshing cache");
    final Future<List<CoinList>> infoFuture = executorService.submit(coinGeckoApiClient::getCoinList);
    final Future<List<AssetData>> ccMetadataFuture = executorService.submit(() ->
        assetDataService.iterativelyLoadTopList(AssetSortBy.CIRCULATING_MKT_CAP_USD, PageRequest.unpaged()));

    final List<CoinList> coins = infoFuture.get();
    log.debug("total fetched coin list size {}", coins.size());
//    final Future<Map<String, Map<String, Double>>> cgMetadata = executorService.submit(() -> {
//      final Map<String, Map<String, Double>> metadata = new HashMap<>();
//      Lists.partition(coins.stream().map(CoinList::getId).toList(), 300).forEach(sublist -> {
//        Map<String, Map<String, Double>> part = coinGeckoApiClient.getUsdPrice(sublist);
//        metadata.putAll(part);
//      });
//      return metadata;
//    });
    log.debug("Fetching CoinGecko metadata for {} items", coins.stream());
    final Map<String, CoinPriceData> cgMetadata = getCgMetadata(coins.subList(0, 100)); //todo sublist for quicker development
    log.debug("Fetched CoinGecko metadata: {} items", cgMetadata.size());
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

    log.debug("tokens selected: {}", tokens.size());
    final List<Token> filteredTokens = filterTokens(tokens, cgMetadata);
    populateCryptoCompareIds(filteredTokens, ccMetadataFuture.get());
    populateTradePair(filteredTokens);
    coinsData.clear();
    coinsData.addAll(filteredTokens);
    log.debug("cache updated");
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

  // todo bug: not all tokens have ccSymbol (CC has less tokens then CG).
  // QUESTION:if CC does not have a token, does it mean that CEXes (supported by CC) has it.
  // We need to know exactly if this is possible (look at the total number of tokens supported by CC to reason about it.
  //   As for now we can skip trade pair population for such tokens.
  // UPDATE: Verify if such token exist (should be fixed)
  //TODO bug#2. ZRX - present on CG but ccSymbol is null (THOUGH it's supported on CC). How is this possible? -- update FIXED, please verify
  private void populateTradePair(List<Token> tokens) {
    long start = System.currentTimeMillis();
    properties.getCexes().forEach(cex -> {
      final Map<String, ExchangeData> availableMarkets = spotDataService.getAvailableMarkets(cex.getCcName(), List.of());
      if(!availableMarkets.containsKey(cex.getCcName())) return;
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
                .filter(token -> token.getCcSymbol().equalsIgnoreCase(instrument.getBase()))
                .forEach(token -> {
                  token.getTradePairs().put(cex, new TradePair(instrument.getBase(), instrument.getQuote()));
                });
          });
    });
    log.debug("Fetch USD trade pair for every CEX: {} items, {}ms", tokens.size(), System.currentTimeMillis() - start);
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
            token.setCcId(assetData.getName());
            token.setCcSymbol(assetData.getSymbol());
          });
    });
    log.debug("populated assets with CryptoCompare metadata: {} items, {}ms", tokens.size(), System.currentTimeMillis() - start);
  }

  public List<Token> getTokens() {
    return new ArrayList<>(coinsData);
  }
}
