package trade.shark.dumpscreener.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
public class DexLiquidityPool {
  private String dexName;
  private TradePair liquidityPoolPair;
  private String liquidityPairAddress;
  private BigDecimal poolLiquidityUsd;

  public String getLiquidityPairAddress() {
    return liquidityPairAddress != null ? liquidityPairAddress.toUpperCase() : null;
  }
}
