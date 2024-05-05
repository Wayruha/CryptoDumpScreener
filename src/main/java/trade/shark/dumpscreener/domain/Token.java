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
public class Token {
  private String cgId;
  private String cgSymbol;
  private String ccId;
  private String ccSymbol;
  @EqualsAndHashCode.Exclude
  private Map<CentralizedExchange, TradePair> tradePairs;
  @EqualsAndHashCode.Exclude
  private List<NetworkContract> contracts;
}
