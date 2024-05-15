package trade.shark.dumpscreener.event;

import lombok.Data;
import trade.shark.dumpscreener.domain.CexSpread;
import trade.shark.dumpscreener.domain.Token;
import trade.shark.dumpscreener.enums.CentralizedExchange;
import trade.shark.dumpscreener.enums.Network;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;

@Data
public class DumpSignalEvent {
  private final LocalDate timestamp;
  private Token token;
  private Network network;
  private BigDecimal currentPrice;
  private BigDecimal priceChange;
  private BigDecimal changePercentage;
  private Duration monitoredTimeWindow;
  private Map<CentralizedExchange, CexSpread> cexOptions;

  public DumpSignalEvent(Token token, Network network, BigDecimal price, BigDecimal change, BigDecimal changePercentage, Duration monitoredTimeWindow) {
    this.timestamp = LocalDate.now();
    this.token = token;
    this.network = network;
    this.currentPrice = price;
    this.priceChange = change;
    this.changePercentage = changePercentage;
    this.monitoredTimeWindow = monitoredTimeWindow;
  }
}
