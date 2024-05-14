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
import trade.wayruha.cryptocompare.domain.Exchange;
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
    final Future<Map<String, Map<String, Double>>> cgMetadata = executorService.submit(() -> {
      final Map<String, Map<String, Double>> metadata = new HashMap<>();
      Lists.partition(coins.stream().map(CoinList::getId).toList(), 300).forEach(sublist -> {
        Map<String, Map<String, Double>> part = coinGeckoApiClient.getUsdPrice(sublist);
        metadata.putAll(part);
      });
      return metadata;
    });
    //TODO populate with cgMetadada and add filters(volume24h, market cap)
    final List<Token> tokens = coins.stream().map(coin -> {
      final Map<String, String> platforms = coin.getPlatforms();
      final List<NetworkContract> contracts = platforms.keySet().stream().map(key -> {
            final Network network = Network.getByCgName(key);
            final NetworkContract networkContract = Optional.ofNullable(network).map(net -> NetworkContract.of(platforms.get(key), net)).orElse(null);
            return properties.getNetworks().contains(network) ? networkContract : null;
          })
          .filter(Objects::nonNull)
          .toList();
      return Token.builder()
          .cgId(coin.getName())
          .cgSymbol(coin.getSymbol())
          .name(coin.getName())
          .tradePairs(new HashMap<>())
          .contracts(contracts)
          .build();
    }).toList();
    mapAndFetchSymbols(tokens, metadataFuture.get());
  }

  private void mapAndFetchSymbols(List<Token> tokens, List<AssetData> metadata) {
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
