package trade.shark.dumpscreener.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import trade.shark.dumpscreener.DumpScreenerApplication;
import trade.shark.dumpscreener.config.AppProperties;
import trade.shark.dumpscreener.config.MonitoringRule;
import trade.shark.dumpscreener.domain.CexSpread;
import trade.shark.dumpscreener.domain.DexLiquidityPool;
import trade.shark.dumpscreener.domain.NetworkContract;
import trade.shark.dumpscreener.domain.Token;
import trade.shark.dumpscreener.enums.CentralizedExchange;
import trade.shark.dumpscreener.event.DumpSignalEvent;
import trade.shark.dumpscreener.event.ExceptionEvent;
import trade.shark.dumpscreener.event.MetadataRefreshedEvent;
import trade.shark.dumpscreener.exception.NotificationException;
import trade.shark.dumpscreener.service.geckoterminal.LPTransaction;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static trade.shark.dumpscreener.util.MathUtil.calculateDeviation;
import static trade.shark.dumpscreener.util.MathUtil.calculateSpread;

@Slf4j
@Component
public class EventHandler {
  private static final int RElEVANT_TRADES_TIMEWINDOW_MILTIPLIER = 2;
  private static final int MAX_RELEVANT_TRADES = 30;
  private static final BigDecimal SIMILAR_PRICE_DEVIATION_FRACTION_THD = new BigDecimal("0.05");

  private final AppProperties appProperties;
  private final CexService cexService;
  private final GeckoTerminalService dexTransactionService;
  private final TgNotificationService notificationService;
  private final BigDecimal changeThreshold;
  private final BigDecimal fakeTradeVolumeThreshold;

  public EventHandler(AppProperties appProperties, CexService cexService, GeckoTerminalService dexTransactionService, TgNotificationService notificationService) {
    this.appProperties = appProperties;
    this.cexService = cexService;
    this.dexTransactionService = dexTransactionService;
    this.notificationService = notificationService;
    final BigDecimal maxTriggerByRules = appProperties.getRules().stream()
        .map(MonitoringRule::getTriggerPercentage)
        .max(Comparator.comparing(Function.identity()))
        .orElse(new BigDecimal(100));
    this.changeThreshold = maxTriggerByRules.max(appProperties.getMaxAllowedPriceChangePercentage());
    this.fakeTradeVolumeThreshold = appProperties.getFakeTradeVolumeThreshold();
  }

  @EventListener
  public void onApplicationReady(ApplicationReadyEvent event) {
    DumpScreenerApplication.CLI_LOG.info("Application has started.");
  }

  @EventListener
  public void onSignalTriggered(DumpSignalEvent event) {
    log.info("Dump signal triggered: {}", event);
    try {
      if (event.getChangePercentage().abs().compareTo(changeThreshold) >= 0) {
        log.warn("Change percentage {} is greater than threshold {}. Skipping.", event.getChangePercentage(), changeThreshold);
        return;
      }

      checkTransactionVolumeStatus(event);

      final Map<CentralizedExchange, CexSpread> options = loadCexOptions(event.getToken(), event.getCurrentPrice());
      event.setCexOptions(options);

      final String displayText = "\n" + TgNotificationService.toTgDisplayText(event);
      DumpScreenerApplication.CLI_LOG.info(displayText);
      sendSignalNotifications(event);
    } catch (Exception ex) {
      log.error("Error processing dump signal for  {}", event.getToken().getPrimaryContract(), ex);
    }
  }

  @EventListener
  public void handleMetadataRefreshed(MetadataRefreshedEvent event) {
    try {
      final String text = String.format("Metadata refreshed in %d sec. Supported tokens: %d", event.getTimeSpend().getSeconds(), event.getSupportedTokens().size());
      DumpScreenerApplication.CLI_LOG.info(text);
      notificationService.sendNotification(text);
    } catch (Exception ex) {
      log.error("Error processing {}", event, ex);
    }
  }

  @EventListener
  public void handleException(ExceptionEvent event) {
    final String msg = String.format("Exception during `%s`: `%s. Please restart application. If it does not help - contact the administrator.`", event.getAction(), event.getException().getMessage());
    DumpScreenerApplication.CLI_LOG.info(msg);
    notificationService.sendNotification(msg);
  }

  private void sendSignalNotifications(DumpSignalEvent event) {
    try {
      notificationService.sendNotifications(event);
    } catch (NotificationException ex) {
      log.error("Error sending notifications for  {}", event.getToken().getPrimaryContract(), ex);
    }
  }

  private Map<CentralizedExchange, CexSpread> loadCexOptions(Token token, BigDecimal currentPrice) {
    final HashMap<CentralizedExchange, CexSpread> options = new HashMap<>();
    try {
      final Map<CentralizedExchange, BigDecimal> dollarPrices = cexService.getDollarPrices(token, appProperties.getCexes());
      dollarPrices.forEach((exchange, cexPrice) -> {
        final BigDecimal spread = calculateSpread(currentPrice, cexPrice);
        final CexSpread value = new CexSpread(exchange, cexPrice, spread);
        options.put(exchange, value);
      });
      log.info("CEX options loaded for {}: {}", token.getPrimaryContract(), options);
    } catch (Exception ex) {
      log.error("Error loading CEX options for token {}", token.getPrimaryContract(), ex);
    }
    return options;
  }

  public void checkTransactionVolumeStatus(DumpSignalEvent event) {
    try {
      final DexLiquidityPool liquidityPool = event.getToken().getDexLiquidityPool();
      final NetworkContract lpAddress = NetworkContract.of(liquidityPool.getLiquidityPairAddress(), event.getNetwork());
      final Long monitoredWindow = event.getDetectedRule().getTimeWindowSec();

      final List<LPTransaction> trades = dexTransactionService.loadPoolTransactions(lpAddress)
          .stream()
          .sorted(Comparator.comparing(LPTransaction::getBlockTimestamp).reversed())
          .limit(MAX_RELEVANT_TRADES)
          .toList();
      final ZonedDateTime lastTradeDate = trades.get(0).getBlockTimestamp();
      final List<LPTransaction> timeRelevantTrades = trades.stream()
          .filter(t -> Duration.between(t.getBlockTimestamp(), lastTradeDate).getSeconds() < monitoredWindow * RElEVANT_TRADES_TIMEWINDOW_MILTIPLIER)
          .filter(t -> calculateDeviation(t.getPriceToInUsd(), event.getCurrentPrice()).compareTo(SIMILAR_PRICE_DEVIATION_FRACTION_THD) < 0)
          .toList();

      if (timeRelevantTrades.isEmpty()) {
        log.warn("No trades in the time window for {}. Trades: {}", event, trades);
        event.setWarning(true);
      } else {
        // get the sum of all trades in the time window
        final BigDecimal totalVolume = timeRelevantTrades.stream()
            .map(LPTransaction::getVolumeInUsd)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (isLowTradeVolume(totalVolume, liquidityPool)) {
          log.warn("Low volume trades trigger signal. Event:{}, trades: {}", event, trades);
          event.setLowVolumeChange(true);
        }
      }
    } catch (Exception ex) {
      log.error("Error while checking last trades for {}.", event, ex);
      event.setWarning(true);
    }
  }

  private boolean isLowTradeVolume(BigDecimal tradeVolume, DexLiquidityPool pool) {
    if (fakeTradeVolumeThreshold == null) return false;
    return tradeVolume.compareTo(fakeTradeVolumeThreshold) < 0;
    // percentage threshold implementation
    //    return calculatePercentage(tradeVolume, pool.getPoolLiquidityUsd()).compareTo(ULTRA_LOW_VOLUME_THD) < 0;
  }
}
