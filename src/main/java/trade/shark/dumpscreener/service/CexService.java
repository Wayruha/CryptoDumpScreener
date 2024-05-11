package trade.shark.dumpscreener.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import trade.shark.dumpscreener.domain.Token;
import trade.shark.dumpscreener.domain.TradePair;
import trade.shark.dumpscreener.enums.CentralizedExchange;
import trade.wayruha.cryptocompare.response.InstrumentLatestTick;
import trade.wayruha.cryptocompare.service.SpotDataService;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

@Slf4j
@RequiredArgsConstructor
@Service
public class CexService {
  private final SpotDataService spotDataService;
  private final ExecutorService executorService;

  public BigDecimal getDollarPrice(Token token, CentralizedExchange exchange) {
    final TradePair tradePair = token.getTradePairs().get(exchange);
    if (tradePair == null) {
      //token is not traded on the exchange
      return null;
    }
    try {
      final String symbol = formatTradePair(tradePair);
      final String formattedExchange = exchange.getExchange().name(); //todo String vs enum
      final Map<String, InstrumentLatestTick> tick = spotDataService.getLatestTick(formattedExchange, List.of(symbol));
      return tick.get(symbol).getLastPrice();
    } catch (Exception ex) {
      log.error("Error getting price for token {} on exchange {}", token, exchange, ex);
      return null;
    }
  }

  /**
   * Fetch prices in parallel for multiple exchanges
   */
  public Map<CentralizedExchange, BigDecimal> getDollarPrices(Token token, Collection<CentralizedExchange> exchanges) {
    final Map<CentralizedExchange, Future<BigDecimal>> futures = new HashMap<>();
    exchanges.forEach(exchange -> futures.put(exchange, executorService.submit(() -> getDollarPrice(token, exchange))));

    final Map<CentralizedExchange, BigDecimal> result = new HashMap<>();
    futures.forEach((exchange, future) -> {
      try {
        final BigDecimal price = future.get();
        if (price != null) {
          result.put(exchange, price);
        }
      } catch (Exception ex) {
        log.error("Error getting price for token {} on exchange {}", token, exchange, ex);
      }
    });
    return result;
  }

  /**
   * formats the asset to match CryptoCompare's format
   */
  private static String formatTradePair(TradePair asset) {
    if (asset == null) return null;
    return asset.format();
  }
}
