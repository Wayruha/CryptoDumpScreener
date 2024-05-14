package trade.shark.dumpscreener.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import trade.wayruha.cryptocompare.domain.Exchange;
import trade.wayruha.oneinch.Chain;

@RequiredArgsConstructor
public enum CentralizedExchange {
  BINANCE("Binance", "binance");

  @Getter
  private final String name;
  @Getter
  private final String ccName;
}
