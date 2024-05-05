package trade.shark.dumpscreener.enums;

import lombok.AllArgsConstructor;

import java.util.stream.Stream;

@AllArgsConstructor
public enum Network {
  ETHEREUM("etherium", ""),
  ARBITRUM("arbitrum", "");

  String cgName;
  String ccName;

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
