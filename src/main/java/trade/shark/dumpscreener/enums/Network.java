package trade.shark.dumpscreener.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import trade.wayruha.oneinch.Chain;

import java.util.stream.Stream;

@AllArgsConstructor
@Getter
public enum Network {
  ETHEREUM("etherium", "", Chain.ETHEREUM),
  ARBITRUM("arbitrum", "", Chain.ARBITRUM);

  final String cgName;
  final String ccName;
  final Chain oneInchChain;

  public static Network getByCgName(String cgName) {
    return Stream.of(values())
        .filter(network -> network.cgName.equalsIgnoreCase(cgName))
        .findFirst()
        .orElse(null);
  }

  public static Network getByCcName(String ccName) {
    return Stream.of(values())
        .filter(network -> network.ccName.equalsIgnoreCase(ccName))
        .findFirst()
        .orElse(null);
  }
}
