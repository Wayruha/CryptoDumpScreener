package trade.shark.dumpscreener.config;

import com.litesoftwares.coingecko.ApiKey;
import com.litesoftwares.coingecko.CoinGeckoApiClient;
import com.litesoftwares.coingecko.impl.CoinGeckoApiClientImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import trade.wayruha.cryptocompare.CryptoCompareParams;
import trade.wayruha.cryptocompare.service.AssetDataService;
import trade.wayruha.cryptocompare.service.SpotDataService;
import trade.wayruha.oneinch.OneInchParams;
import trade.wayruha.oneinch.service.SpotService;

import java.math.MathContext;
import java.math.RoundingMode;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class ApplicationConfig {
  @Bean
  public WebClient webClient() {
    return WebClient.create();
  }

  @Bean
  public ExecutorService executor() {
    //todo use more, depending on the CEXes count
    return Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
  }

  @Bean
  public CoinGeckoApiClient coinGeckoApiClient(AppProperties properties) {
    return new CoinGeckoApiClientImpl(new ApiKey(properties.getCoingeco().getApiKey(), false));
  }

  @Bean
  public AssetDataService assetDataService(AppProperties properties) {
    final CryptoCompareParams params = new CryptoCompareParams();
    params.setApiKey(properties.getCryptoCompare().getApiKey());
    return new AssetDataService(params);
  }

  @Bean
  public SpotDataService spotDataService(AppProperties properties) {
    //todo initialize it correctly
    final CryptoCompareParams params = new CryptoCompareParams();
    params.setApiKey(properties.getCryptoCompare().getApiKey());
    return new SpotDataService(params);
  }

  @Bean
  public SpotService oneinchSpotService(AppProperties properties) {
    final OneInchParams params = new OneInchParams(properties.getOneInch().getApiKey());
    return new SpotService(params);
  }

  @Bean
  public MathContext mathContext() {
    return new MathContext(7, RoundingMode.FLOOR);
  }
//  @Bean
//  public SpotDataService spotDataService() {
//    return new SpotDataService();
//  }
}
