package trade.shark.dumpscreener.domain;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import trade.shark.dumpscreener.enums.CentralizedExchange;

import java.util.List;
import java.util.Map;

@Data
@Builder
@EqualsAndHashCode
public class Token implements TokenMetadata {
  private String cgId;
  private String cgSymbol;
  private String ccId;
  private String ccSymbol;
  private String name;
  @EqualsAndHashCode.Exclude
  private Map<CentralizedExchange, TradePair> tradePairs;
  @EqualsAndHashCode.Exclude
  private List<NetworkContract> contracts;

  //returns the primary token contract
  @Override
  public NetworkContract getIdentityContract() {
    //todo may not be an optional implementation.
    // Let's consider case when original token is in ETH but there's also a bridged token in BSC
    return contracts.stream().findFirst().orElse(null);
  }

  @Override
  public String getSymbol() {
    return cgSymbol;
  }

  @Override
  public String getName() {
    return name;
  }
}
