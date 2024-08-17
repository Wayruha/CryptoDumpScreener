package trade.shark.dumpscreener.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import trade.shark.dumpscreener.enums.Network;

import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
public class DexLiquidityPool {
  private String dexName;
  private TradePair liquidityPoolPair;
  private String liquidityPairAddress;
  private Network network;
  private BigDecimal poolLiquidityUsd;

  public NetworkContract getContract() {
    return NetworkContract.of(liquidityPairAddress, network);
  }
}
