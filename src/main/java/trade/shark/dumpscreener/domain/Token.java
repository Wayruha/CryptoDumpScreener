package trade.shark.dumpscreener.domain;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import trade.shark.dumpscreener.enums.CentralizedExchange;
import trade.shark.dumpscreener.enums.Network;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@Builder
@EqualsAndHashCode
public class Token implements TokenMetadata {
  private String coingeckoId;
  private String coingeckoSymbol;
  private Integer cryptoCompareId;
  private String cryptoCompareSymbol;
  private String name;
  private Long deploymentTime;
  private BigDecimal marketCap;
  private BigDecimal usdVolume24H;
  @EqualsAndHashCode.Exclude
  @ToString.Exclude
  private Map<CentralizedExchange, TradePair> tradePairs;
  @EqualsAndHashCode.Exclude
  @ToString.Exclude
  private List<NetworkContract> contracts;
  private DexLiquidityPool dexLiquidityPool;
  private NetworkContract primaryContract;

  public String getContractAddress(Network network) {
    return contracts.stream()
        .filter(contract -> contract.getNetwork() == network)
        .map(NetworkContract::getContractAddress)
        .findFirst()
        .orElse(null);
  }

  @Override
  public String getSymbol() {
    return coingeckoSymbol;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public Long deploymentTime() {
    return deploymentTime;
  }

}
