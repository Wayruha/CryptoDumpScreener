package trade.shark.dumpscreener.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import trade.shark.dumpscreener.DumpScreenerApplication;
import trade.shark.dumpscreener.config.AppProperties;
import trade.shark.dumpscreener.domain.CexSpread;
import trade.shark.dumpscreener.domain.Token;
import trade.shark.dumpscreener.enums.CentralizedExchange;
import trade.shark.dumpscreener.event.DumpSignalEvent;
import trade.shark.dumpscreener.event.ExceptionEvent;
import trade.shark.dumpscreener.event.MetadataRefreshedEvent;
import trade.shark.dumpscreener.exception.NotificationException;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static trade.shark.dumpscreener.util.MathUtil.calculateSpread;

@Slf4j
@Component
public class EventHandler {
  private final AppProperties appProperties;
  private final CexService cexService;
  private final TgNotificationService notificationService;
  private final BigDecimal changeThreshold;

  public EventHandler(AppProperties appProperties, CexService cexService, TgNotificationService notificationService) {
    this.appProperties = appProperties;
    this.cexService = cexService;
    this.notificationService = notificationService;
    final BigDecimal maxTriggerByRules = appProperties.getRules().stream()
        .map(AppProperties.Rule::getTriggerPercentage)
        .max(Comparator.comparing(Function.identity()))
        .orElse(new BigDecimal(100));
    this.changeThreshold = maxTriggerByRules.max(appProperties.getMaxAllowedPriceChangePercentage());
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

      final Map<CentralizedExchange, CexSpread> options = loadCexOptions(event.getToken(), event.getCurrentPrice());
      event.setCexOptions(options);

      final String displayText = "\n" + TgNotificationService.toTgDisplayText(event);
      DumpScreenerApplication.CLI_LOG.info(displayText);
      sendSignalNotifications(event);
    } catch (Exception ex) {
      log.error("Error processing dump signal for  {}", event.getToken().getIdentityContract(), ex);
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
    final String msg = String.format("Exception during `%s`: `%s. Please contact administrator.`", event.getAction(), event.getException().getMessage());
    DumpScreenerApplication.CLI_LOG.info(msg);
    notificationService.sendNotification(msg);
  }

  private void sendSignalNotifications(DumpSignalEvent event) {
    try {
      notificationService.sendNotifications(event);
    } catch (NotificationException ex) {
      log.error("Error sending notifications for  {}", event.getToken().getIdentityContract(), ex);
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
      log.info("CEX options loaded for {}: {}", token.getIdentityContract(), options);
    } catch (Exception ex) {
      log.error("Error loading CEX options for token {}", token.getIdentityContract(), ex);
    }
    return options;
  }
}
