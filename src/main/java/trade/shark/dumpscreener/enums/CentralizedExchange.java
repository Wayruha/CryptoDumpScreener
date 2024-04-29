package trade.shark.dumpscreener.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum CentralizedExchange {
  BINANCE("Binance");

  @Getter
  private final String name;
}
