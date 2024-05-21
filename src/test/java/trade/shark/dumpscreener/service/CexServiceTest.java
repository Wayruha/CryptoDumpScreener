package trade.shark.dumpscreener.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.junit.jupiter.api.Test;
import trade.shark.dumpscreener.config.AppProperties;
import trade.shark.dumpscreener.domain.Token;
import trade.shark.dumpscreener.domain.TradePair;
import trade.shark.dumpscreener.enums.CentralizedExchange;
import trade.wayruha.cryptocompare.CryptoCompareParams;
import trade.wayruha.cryptocompare.client.CryptoCompareClient;
import trade.wayruha.cryptocompare.service.SpotDataService;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class CexServiceTest {
  static final String apiKey = "";
  CexService service = new CexService(spotDataService(getCryptoCompareClient(getProperties())), null);

  @Test
  void testGetPrice() {
    Map<CentralizedExchange, TradePair> pairs = Map.of(CentralizedExchange.BINANCE, new TradePair("BTC", "USDT"));
    final Token token = Token.builder().tradePairs(pairs).build();
    assertNotNull(service.getDollarPrice(token, CentralizedExchange.BINANCE));
  }

  private static AppProperties getProperties() {
    final AppProperties appProperties = new AppProperties();
    final AppProperties.CryptoCompare cryptoCompare = new AppProperties.CryptoCompare();
    appProperties.setCryptoCompare(cryptoCompare);
    cryptoCompare.setApiKey(apiKey);
    return appProperties;
  }

  public static SpotDataService spotDataService(CryptoCompareClient client) {
    return new SpotDataService(client);
  }

  public static CryptoCompareClient getCryptoCompareClient(AppProperties properties) {
    final CryptoCompareParams params = new CryptoCompareParams();
    params.setApiKey(properties.getCryptoCompare().getApiKey());

    final ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    final CryptoCompareClient client = new CryptoCompareClient(params, objectMapper);
    return client;
  }
}