package trade.shark.dumpscreener.service;

import com.google.common.collect.Lists;
import com.litesoftwares.coingecko.CoinGeckoApiClient;
import com.litesoftwares.coingecko.domain.Coins.CoinList;
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

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

//todo it should hold the metadata returned by aggregator API (coingecko?)
@Service
@Slf4j
@RequiredArgsConstructor
public class MetadataService {
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
    final Future<List<AssetData>> metadataFuture = executorService.submit(() ->
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
    log.debug("getting cg metadata");
    final Map<String, Map<String, Double>> cgMetadata = getCgMetadata(coins);
    log.debug("retrieved cg metadata");
    final List<Token> tokens = coins.stream().map(coin -> {
          final Map<String, String> platforms = coin.getPlatforms();
          final List<NetworkContract> contracts = platforms.keySet().stream().map(key -> {
                final Network network = Network.getByCgName(key);
                final NetworkContract networkContract = Optional.ofNullable(network).map(net -> NetworkContract.of(platforms.get(key), net)).orElse(null);
                return properties.getNetworks().contains(network) ? networkContract : null;
              })
              .filter(Objects::nonNull)
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
    mapAndFetchSymbols(filterTokens(tokens, cgMetadata), metadataFuture.get());
  }

  private Map<String, Map<String, Double>> getCgMetadata(List<CoinList> coins) throws InterruptedException {
    final Map<String, Map<String, Double>> metadata = new HashMap<>();
    StringBuilder sublistBuilder = new StringBuilder();
    List<String> sublist = new ArrayList<>();

    for (CoinList coin : coins) {
      final String coinId = coin.getId();
      final int coinIdLength = coinId.length();

      if (sublistBuilder.length() + coinIdLength + 1 > 5000) {
        processSublist(sublist, metadata);
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
      processSublist(sublist, metadata);
    }

    return metadata;
  }

  private void processSublist(List<String> sublist, Map<String, Map<String, Double>> metadata) throws InterruptedException {
    log.debug("fetching partial cg metadata: {}", sublist);
    Thread.sleep(2500);
    final Map<String, Map<String, Double>> part = coinGeckoApiClient.getUsdPrice(sublist);
    metadata.putAll(part);
  }

  private List<Token> filterTokens(List<Token> tokens, Map<String, Map<String, Double>> cgMetadata) {
    log.debug("filtering tokens");
    final List<Token> filtered = tokens.stream()
        .filter(token -> {
          final Optional<Map<String, Double>> tokenData = cgMetadata.keySet().stream().filter(cgToken -> Objects.equals(token.getCgId(), cgToken)).findFirst()
              .map(cgMetadata::get);
          if (tokenData.isEmpty()) {
            return false;
          }
          final boolean matches24hVolume = properties.getVolume24h() <= Optional.ofNullable(tokenData.get().get("usd_24h_vol")).orElse(-1D);
          final boolean matchesMarketCap = properties.getMarketCap() <= Optional.ofNullable(tokenData.get().get("usd_market_cap")).orElse(-1D);
          return matchesMarketCap && matches24hVolume;
        }).toList();
    log.debug("filtered tokens: {}", filtered.size());
    return filtered;
  }

  private void mapAndFetchSymbols(List<Token> tokens, List<AssetData> metadata) {
    log.debug("fetching cc metadata");
    final Map<String, AssetData> assetDataMap = metadata.stream()
        .flatMap(assetData -> assetData.getSupportedPlatforms().stream()
            .map(platform -> new AbstractMap.SimpleEntry<>(platform.getSmartContractAddress(), assetData)))
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    tokens.forEach(token -> {
      for (NetworkContract contract : token.getContracts()) {
        AssetData assetData = assetDataMap.get(contract.getContractAddress());
        if (assetData != null) {
          token.setCcId(assetData.getName());
          token.setCcSymbol(assetData.getSymbol());
          break;
        }
      }
    });
    log.debug("populated assets with metadata: {}", tokens.size());

    properties.getCexes().forEach(cex -> {
      final Map<String, ExchangeData> response = spotDataService.getAvailableMarkets(cex.getCcName(), List.of());
      final Optional<ExchangeData> data = Optional.ofNullable(response.get(cex.getCcName()));
      if (data.isPresent()) {
        final List<InstrumentMapping> instruments = data.get().getInstruments().values().stream().map(InstrumentData::getInstrumentMapping).toList();
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
      }
    });

    setTokens(tokens);
    log.debug("cache updated");
  }

  private void setTokens(List<Token> tokens) {
    coinsData.clear();
    coinsData.addAll(tokens);
  }

  public List<Token> getTokens() {
    return new ArrayList<>(coinsData);
  }
}
