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
  GATE("Gate", "gateio"),
  KRAKEN("Kraken", "kraken"),
  LBANK("Lbank", "lbank"),
  MEXC("Mexc", "mexc"),
  BYBIT("Bybit", "bybit"),
  WHITEBIT("Whitebit", "whitebit"),
  BITRUE("Bitrue", "bitrue"),
  XT("Xt", "xtpub"),
  PROBIT("Probit", "probit"),
  BITFINEX("Bitfinex", "bitfinex");
  //  CRYPTOCOM, not available

  @Getter
  private final String name;
  @Getter
  private final String ccName;
}
