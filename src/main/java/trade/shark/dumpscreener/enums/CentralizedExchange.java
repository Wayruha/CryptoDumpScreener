package trade.shark.dumpscreener.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum CentralizedExchange {
  BINANCE("Binance", "binance"),
  OKX("Okx", "okex"),
  BITMART("Bitmart", "bitmart"),
  KUCOIN("Kucoin", "kucoin"),
  HUOBI("Huobi", "huobipro"),
  GATE("Gate", "gateio");

  @Getter
  private final String name;
  @Getter
  private final String ccName;
}
