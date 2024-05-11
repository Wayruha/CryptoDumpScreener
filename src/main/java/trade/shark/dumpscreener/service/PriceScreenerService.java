package trade.shark.dumpscreener.service;

import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import trade.shark.dumpscreener.config.AppProperties;
import trade.shark.dumpscreener.domain.Token;
import trade.shark.dumpscreener.domain.TradePair;
import trade.shark.dumpscreener.enums.CentralizedExchange;
import trade.wayruha.cryptocompare.response.InstrumentLatestTick;
import trade.wayruha.cryptocompare.service.SpotDataService;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.*;

@Service
@Slf4j
public class PriceScreenerService {
  private static final Deque<Map<Token, BigDecimal>> priceMaps = new ArrayDeque<>();
  private final MetadataService metadataService;
  private final NotificationService notificationService;
  private final SpotDataService spotDataService;
  private final AppProperties properties;
  private final Long priceMapsToMaintain;
  private final MathContext mathContext;

  public PriceScreenerService(MetadataService metadataService,
                              SpotDataService spotDataService,
                              NotificationService notificationService,
                              AppProperties properties,
                              MathContext mathContext) {
    this.metadataService = metadataService;
    this.properties = properties;
    this.mathContext = mathContext;
    this.notificationService = notificationService;
    this.spotDataService = spotDataService;
    this.priceMapsToMaintain = properties.getDumpPeriod() / properties.getScreeningRate();
  }

  private void fetchPriceUpdates() {
    final List<Token> tokens = metadataService.getTokens();
    final Map<Token, BigDecimal> priceMap = new HashMap<>();
    Lists.partition(tokens, 1000).forEach(sublist -> {
      //TODO use 1inch api to fetch prices and build map
    });

    if (priceMaps.size() >= priceMapsToMaintain) {
      priceMaps.removeFirst();
    }
    priceMaps.push(priceMap);
  }

  public void detectDumps() {
    fetchPriceUpdates();
    final Map<Token, BigDecimal> old = priceMaps.getFirst();
    final Map<Token, BigDecimal> current = priceMaps.getLast();

    old.keySet().forEach(key -> {
      final BigDecimal oldPrice = old.get(key);
      final BigDecimal currentPrice = current.get(key);
      if (oldPrice != null && currentPrice != null) {
        final BigDecimal change = oldPrice.divide(new BigDecimal(100), mathContext).multiply(currentPrice.subtract(oldPrice), mathContext).negate();
        if (change.compareTo(properties.getTriggerPercentage()) >= 0) {
          log.info(String.format("caught dump for: %s", key));
          final Map<CentralizedExchange, TradePair> exchanges = key.getTradePairs();
          //todo use CexService.getDollarPrices to get the prices in dollars
          exchanges.keySet().forEach(cex -> {
            final Map<String, InstrumentLatestTick> prices = spotDataService.getLatestTick(cex.getExchange(), List.of(exchanges.get(cex).format()));
            //TODO implement
              //todo new DumpSignalHandler();
//            notificationService.sendNotifications();//todo just propagate DumpSignalHandler
          });
        }
      }
    });
  }
}
