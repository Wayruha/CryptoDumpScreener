package trade.shark.dumpscreener.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import trade.shark.dumpscreener.enums.CentralizedExchange;
import trade.shark.dumpscreener.enums.Network;

import java.math.BigDecimal;
import java.util.List;

@Data
@Configuration
@ConfigurationProperties(prefix = "screener")
public class AppProperties {
  private List<String> stableCoins;
  private List<Network> networks;
  private List<CentralizedExchange> cexes;
  private CoinGeco coingeco;
  private CryptoCompare cryptoCompare;
  private OneInch oneInch;
  private Long screeningRate;
  private List<Rule> rules;

  @Data
  public static class CoinGeco {
    private String apiKey;
  }
  @Data
  public static class CryptoCompare {
    private String apiKey;
  }
  @Data
  public static class OneInch {
    private String apiKey;
  }

  @Data
  public static class Rule {
    private BigDecimal triggerPercentage;
    private Long dumpPeriod;
  }
}
