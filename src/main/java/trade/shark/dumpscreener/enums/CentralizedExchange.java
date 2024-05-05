package trade.shark.dumpscreener.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import trade.wayruha.cryptocompare.domain.Exchange;

@RequiredArgsConstructor
public enum CentralizedExchange {
  BINANCE("Binance");

  @Getter
  private final String name;
  @Getter
  private Exchange exchange;
}
