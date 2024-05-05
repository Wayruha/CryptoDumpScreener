package trade.shark.dumpscreener.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import static trade.shark.dumpscreener.config.GlobalConstants.TRADE_PAIR_DIVIDER;

@Data
@Builder
@AllArgsConstructor
public class TradePair {
  private String base;
  private String quote;

  public String format() {
    return base.toUpperCase() + TRADE_PAIR_DIVIDER + quote.toUpperCase();
  }
}
