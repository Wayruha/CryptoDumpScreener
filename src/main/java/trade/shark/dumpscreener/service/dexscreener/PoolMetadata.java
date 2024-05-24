package trade.shark.dumpscreener.service.dexscreener;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PoolMetadata {
  private String chainId;
  private String dexId;
  private String pairAddress;
  private Token baseToken;
  private Token quoteToken;
  private BigDecimal priceNative;
  private BigDecimal priceUsd;
  private Map<String, BigDecimal> volume;
  private Liquidity liquidity;
  private Long pairCreatedAt;

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Token {
    private String address;
    private String name;
    private String symbol;
  }

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Liquidity {
    private BigDecimal usd;
    private BigDecimal base;
    private BigDecimal quote;
  }
}
