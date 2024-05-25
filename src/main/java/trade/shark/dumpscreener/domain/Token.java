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
  private DexLiquidityPool dexLiquidityPool;
  @EqualsAndHashCode.Exclude
  @ToString.Exclude
  private Map<CentralizedExchange, TradePair> tradePairs;
  @EqualsAndHashCode.Exclude
  @ToString.Exclude
  private List<NetworkContract> contracts;

  //returns the primary token contract
  @Override
  public NetworkContract getIdentityContract() {
    //todo may not be an optional implementation.
    // Let's consider case when original token is in ETH but there's also a bridged token in BSC
    return contracts.stream().findFirst().orElse(null);
  }

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
