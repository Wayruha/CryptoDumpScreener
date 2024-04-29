package trade.shark.dumpscreener.service;

import trade.shark.dumpscreener.domain.Token;
import trade.shark.dumpscreener.enums.CentralizedExchange;

import java.math.BigDecimal;

//todo it may use CryptoCompare or CoinGecko API to get the prices
public class CexService {
  //todo return the price of the token in dollars
  public BigDecimal getDollarPrice(Token token, CentralizedExchange exchange) {
    return BigDecimal.ZERO;
  }
}
