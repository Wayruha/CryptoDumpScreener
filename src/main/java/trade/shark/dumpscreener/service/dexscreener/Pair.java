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
public class Pair {
  private Token baseToken;
  private Map<String, BigDecimal> volume;
  private Map<String, BigDecimal> liquidity;

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Token {
    private String address;
    private String name;
    private String symbol;
  }
}
