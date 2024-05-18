package trade.shark.dumpscreener.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import trade.shark.dumpscreener.domain.Token;
import trade.shark.dumpscreener.event.DumpSignalEvent;
import trade.shark.dumpscreener.exception.NotificationException;

import java.util.Optional;

import static trade.shark.dumpscreener.util.MathUtil.alternativeMoneyFormat;
import static trade.shark.dumpscreener.util.MathUtil.calculateAgeInDays;
import static trade.shark.dumpscreener.util.MathUtil.formatPrice;
import static trade.shark.dumpscreener.util.MathUtil.getFormattedSpread;

@Service
@Slf4j
public class TgNotificationService {
  private static final String DEX_1inch = "1inch";

  private static final String SIGNAL_CEXES_TEMPLATE = "  ${exchange}: ${spread}\n";
  private static final String MSG_DIVIDER = "---------------------\n";
  private static final String SIGNAL_MSG_TEMPLATE = """
      *${symbol}/${priceChangePercent}%*
              
      Address: `${tokenAddress}`
      Network: `${tokenNetwork}`
      Name: *${tokenName}*
      MarketCap: ${tokenMarketCap}$
      Age: ${tokenAge}
      Dex:
        Market: ${dexMarket}
        Price: ${dexPrice}
      """;

  private final TelegramClient telegram;

  public TgNotificationService(Optional<TelegramClient> telegram) {
    this.telegram = telegram.orElse(null);
    telegram.ifPresent(t -> log.info("NotificationService is enabled: telegram"));
  }

  public void sendNotifications(DumpSignalEvent event) {
    final String text = toTgDisplayText(event);
    sendNotification(text);
  }

  public void sendNotification(String text) {
    try {
      telegram.sendNotification(text, true);
    } catch (Exception ex) {
      throw new NotificationException(ex);
    }
  }

  public static String toTgDisplayText(DumpSignalEvent event) {
    final Token token = event.getToken();
    final StringBuilder bldr = new StringBuilder(MSG_DIVIDER);
    bldr.append(SIGNAL_MSG_TEMPLATE.replace("${symbol}", token.getSymbol().toUpperCase())
        .replace("${priceChangePercent}", getFormattedSpread(event.getChangePercentage()))
        .replace("${tokenAddress}", token.getContractAddress(event.getNetwork()))
        .replace("${tokenNetwork}", event.getNetwork().toString())
        .replace("${tokenName}", token.getName())
        .replace("${tokenMarketCap}", alternativeMoneyFormat(token.getMarketCap()))
        .replace("${tokenAge}", token.getDeploymentTime() != null ? calculateAgeInDays(token.getDeploymentTime()) + " days" : "--")
        .replace("${dexMarket}", DEX_1inch)
        .replace("${dexPrice}", formatPrice(event.getCurrentPrice())));
    if (!event.getCexOptions().isEmpty()) {
      bldr.append("__CEXes__:\n");
    }
    event.getCexOptions().values().forEach(spread -> {
      bldr.append(SIGNAL_CEXES_TEMPLATE
          .replace("${exchange}", spread.getSellExchange().toString())
          .replace("${spread}", getFormattedSpread(spread.getSpreadPercentage())));
    });
    bldr.append(MSG_DIVIDER);
    return bldr.toString();
  }
}
