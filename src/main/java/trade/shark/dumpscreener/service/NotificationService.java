package trade.shark.dumpscreener.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import trade.shark.dumpscreener.event.DumpSignalEvent;

import java.util.Optional;

@Service
@Slf4j
public class NotificationService {
  private final TelegramClient telegram;

  public NotificationService(Optional<TelegramClient> telegram) {
    this.telegram = telegram.orElse(null);
    telegram.ifPresent(t -> log.info("NotificationService is enabled: telegram"));
  }

  public void sendNotifications(DumpSignalEvent event) {
    final String text = toMessageText(event);
    telegram.sendNotification(text);
  }

  //todo format message text
  private static String toMessageText(DumpSignalEvent event) {
    final String s = """
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
    return s;
  }

}
