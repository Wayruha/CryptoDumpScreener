package trade.shark.dumpscreener.service;

import com.google.common.collect.Comparators;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import trade.shark.dumpscreener.config.AppProperties;
import trade.shark.dumpscreener.domain.NetworkContract;
import trade.shark.dumpscreener.domain.Token;
import trade.shark.dumpscreener.domain.TradePair;
import trade.shark.dumpscreener.enums.CentralizedExchange;
import trade.shark.dumpscreener.enums.Network;
import trade.shark.dumpscreener.event.DumpSignalEvent;
import trade.wayruha.cryptocompare.response.InstrumentLatestTick;
import trade.wayruha.cryptocompare.service.SpotDataService;
import trade.wayruha.oneinch.service.SpotService;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class PriceScreenerService {
  private static final List<Map<Token, BigDecimal>> priceMaps = new ArrayList<>();
  private final MetadataService metadataService;
  private final NotificationService notificationService;
  private final SpotDataService spotDataService;
  private final SpotService oneInchSpotService;
  private final CexService cexService;
  private final ApplicationEventPublisher eventPublisher;
  private final AppProperties properties;
  private final Long priceMapsToMaintain;
  private final MathContext mathContext;

  public PriceScreenerService(MetadataService metadataService,
                              SpotDataService spotDataService,
                              CexService cexService,
                              NotificationService notificationService,
                              ApplicationEventPublisher eventPublisher,
                              SpotService oneInchSpotService,
                              AppProperties properties,
                              MathContext mathContext) {
    this.metadataService = metadataService;
    this.properties = properties;
    this.mathContext = mathContext;
    this.notificationService = notificationService;
    this.spotDataService = spotDataService;
    this.cexService = cexService;
    this.eventPublisher = eventPublisher;
    this.oneInchSpotService = oneInchSpotService;
    this.priceMapsToMaintain = properties.getRules().stream().map(AppProperties.Rule::getDumpPeriod).max(Long::compareTo).orElse(0L) / properties.getScreeningRate();
  }

  private void fetchPriceUpdates() {
    final List<Token> tokens = metadataService.getTokens();
    final Map<Token, BigDecimal> priceMap = new HashMap<>();
    final Map<Network, List<Pair<Token, String>>> groupedTokens = tokens.stream()
        .map(token -> {
          final Optional<NetworkContract> firstNetwork = token.getContracts().stream().findFirst(); // Extract the first network
          return firstNetwork.map(net -> new AbstractMap.SimpleEntry<>(net.getNetwork(), Pair.of(token, net.getContractAddress()))).orElse(null); // Pair it with the token
        })
        .filter(Objects::nonNull)
        .collect(Collectors.groupingBy(AbstractMap.SimpleEntry::getKey,
            Collectors.mapping(AbstractMap.SimpleEntry::getValue, Collectors.toList())));
    groupedTokens.forEach((key, value) -> {
      Lists.partition(value, 5100).forEach(sublist -> {
        final List<String> contracts = sublist.stream().map(Pair::getRight).toList();
        final Map<String, BigDecimal> prices = oneInchSpotService.getTokenDollarPrices(key.getOneInchChain(), contracts);
        sublist.forEach(pair -> {
          String contract = pair.getRight();
          BigDecimal price = prices.get(contract);
          if (price != null) {
            priceMap.put(pair.getLeft(), price);
          }
        });
      });
    });

    if (priceMaps.size() >= priceMapsToMaintain) {
      priceMaps.removeFirst();
    }
    priceMaps.add(priceMap);
  }

  public void detectDumps() {
    fetchPriceUpdates();
    properties.getRules().forEach(this::detectByRule);
  }

  private void detectByRule(AppProperties.Rule rule) {
    final Map<Token, BigDecimal> old = getOldPriceMapForRule(rule);
    final Map<Token, BigDecimal> current = priceMaps.getLast();

    old.keySet().forEach(key -> {
      final BigDecimal oldPrice = old.get(key);
      final BigDecimal currentPrice = current.get(key);
      if (oldPrice != null && currentPrice != null) {
        final BigDecimal change = oldPrice.divide(new BigDecimal(100), mathContext).multiply(currentPrice.subtract(oldPrice), mathContext).negate();
        if (change.compareTo(rule.getTriggerPercentage()) >= 0) {
          log.info(String.format("caught dump for: %s", key));
          final Map<CentralizedExchange, TradePair> exchanges = key.getTradePairs();
          final Map<CentralizedExchange, BigDecimal> prices = cexService.getDollarPrices(key, exchanges.keySet());
          final DumpSignalEvent event = new DumpSignalEvent();
          //TODO remove after tests
//          exchanges.keySet().forEach(cex -> {
//            final Map<String, InstrumentLatestTick> prices = spotDataService.getLatestTick(cex.getExchange(), List.of(exchanges.get(cex).format()));
//            //TODO implement
//              //todo new DumpSignalHandler();
////            notificationService.sendNotifications();//todo just propagate DumpSignalHandler
//          });
        }
      }
    });
  }

  public Map<Token, BigDecimal> getOldPriceMapForRule(AppProperties.Rule rule) {
    int index = Math.max(0, priceMaps.size() - (int) (rule.getDumpPeriod() / properties.getScreeningRate()));
    return priceMaps.get(Math.min(index, priceMaps.size() - 1));
  }
}
