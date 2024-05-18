package trade.shark.dumpscreener.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import trade.shark.dumpscreener.event.DumpSignalEvent;
import trade.shark.dumpscreener.exception.NotificationException;

import java.util.Optional;

import static java.lang.String.valueOf;
import static trade.shark.dumpscreener.util.MathUtil.calculateAgeInDays;
import static trade.shark.dumpscreener.util.MathUtil.formatPrice;
import static trade.shark.dumpscreener.util.MathUtil.getFormattedSpread;

@Service
@Slf4j
public class TgNotificationService {
  private static final String DEX_1inch = "1inch";
  private static final String SIGNAL_MSG_TEMPLATE = """
      __${symbol}/${priceChangePercent}__
              
      Address: *${tokenAddress}*
      Network: ${tokenNetwork}
      Name: ${tokenName}
      Age: ${tokenAge}
      Dex:
        Market: ${dexMarket}
        Price: ${dexPrice}
      CEXes:
      """;
  private static final String SIGNAL_CEXES_TEMPLATE = "  ${exchange}: ${spread}\n";

  private final TelegramClient telegram;

  public TgNotificationService(Optional<TelegramClient> telegram) {
    this.telegram = telegram.orElse(null);
    telegram.ifPresent(t -> log.info("NotificationService is enabled: telegram"));
  }

  public void sendNotifications(DumpSignalEvent event) {
    final String text = toTgDisplayText(event);
    sendNotification(text);
  }

  public static String toTgDisplayText(DumpSignalEvent event) {
    StringBuilder bldr = new StringBuilder();
    bldr.append(SIGNAL_MSG_TEMPLATE.replace("${symbol}", event.getToken().getSymbol())
        .replace("${priceChangePercent}", getFormattedSpread(event.getChangePercentage()))
        .replace("${tokenAddress}", event.getToken().getContractAddress(event.getNetwork()))
        .replace("${tokenNetwork}", event.getNetwork().toString())
        .replace("${tokenName}", event.getToken().getName())
        .replace("${tokenAge}", valueOf(calculateAgeInDays(event.getToken().getDeploymentTime())))
        .replace("${dexMarket}", DEX_1inch)
        .replace("${dexPrice}", formatPrice(event.getCurrentPrice())));
    event.getCexOptions().values().forEach(spread -> {
      bldr.append(SIGNAL_CEXES_TEMPLATE
          .replace("${exchange}", spread.getSellExchange().toString())
          .replace("${spread}", getFormattedSpread(spread.getSpreadPercentage())));
    });
    return bldr.toString();
  }

  public void sendNotification(String text) {
    try {
      telegram.sendNotification(text);
    } catch (Exception ex) {
      throw new NotificationException(ex);
    }
  }

}
