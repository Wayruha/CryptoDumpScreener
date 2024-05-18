package trade.shark.dumpscreener.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import trade.shark.dumpscreener.enums.CentralizedExchange;
import trade.shark.dumpscreener.enums.Network;

import java.math.BigDecimal;
import java.util.List;

import static org.apache.commons.lang3.StringUtils.isBlank;

@Data
@Configuration
@ConfigurationProperties(prefix = "screener")
public class AppProperties {
  private List<String> stableCoins;
  private List<Network> networks;
  private List<CentralizedExchange> cexes;
  private CoinGecko coingecko;
  private CryptoCompare cryptoCompare;
  private OneInch oneInch;
  private Long metadataUpdateRateSec;
  private Long screeningRateSec;
  private List<Rule> rules;
  private BigDecimal volume24h;
  private BigDecimal marketCap;

  @Data
  public static class CoinGecko {
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
    private Long timeWindowSec;
  }

  @PostConstruct
  public void validate() {
    if (isBlank(coingecko.getApiKey())) {
      throw new IllegalArgumentException("Coingecko API key is required.");
    }
    if (isBlank(cryptoCompare.getApiKey())) {
      throw new IllegalArgumentException("CryptoCompare API key is required.");
    }
    if (isBlank(oneInch.getApiKey())) {
      throw new IllegalArgumentException("1Inch API key is required.");
    }

    if (metadataUpdateRateSec == null || metadataUpdateRateSec < 3600) {
      throw new IllegalArgumentException("metadataUpdateRateSec should be at least one hour (3600sec).");
    }

    if (screeningRateSec == null || screeningRateSec < 10) {
      throw new IllegalArgumentException("screeningRateSec should be at least 10seconds");
    }

    if(rules == null || rules.isEmpty()){
      throw new IllegalArgumentException("At least one detection rule should be defined.");
    }
  }
}
