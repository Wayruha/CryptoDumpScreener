package trade.shark.dumpscreener.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import trade.shark.dumpscreener.config.AppProperties;
import trade.shark.dumpscreener.domain.CexSpread;
import trade.shark.dumpscreener.domain.Token;
import trade.shark.dumpscreener.enums.CentralizedExchange;
import trade.shark.dumpscreener.event.DumpSignalEvent;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static trade.shark.dumpscreener.util.MathUtil.calculateSpread;

@Slf4j
@Component
@RequiredArgsConstructor
public class DumpSignalHandler {
  private final AppProperties appProperties;
  private final CexService cexService;
  private final NotificationService notificationService;


  @EventListener
  public void onSignalTriggered(DumpSignalEvent event) {
    log.info("Dump signal triggered: {}", event);
    final Map<CentralizedExchange, CexSpread> options = loadCexOptions(event.getToken(), event.getCurrentPrice());
    event.setCexOptions(options);

    notificationService.sendNotifications(event);
  }

  public Map<CentralizedExchange, CexSpread> loadCexOptions(Token token, BigDecimal currentPrice) {
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
