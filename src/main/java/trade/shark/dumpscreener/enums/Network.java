package trade.shark.dumpscreener.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import trade.wayruha.oneinch.Chain;

import java.util.stream.Stream;

@AllArgsConstructor
@Getter
public enum Network {
  ETHEREUM("ethereum", "ETH", Chain.ETHEREUM, "ethereum"),
  ARBITRUM("arbitrum", "ARB", Chain.ARBITRUM, "arbitrum");

  final String coingeckoName;
  final String cryptoCompareName;
  final Chain oneInchChain;
  final String dexScreenerName;

  public static Network getByCgName(String cgName) {
    return Stream.of(values())
        .filter(network -> network.coingeckoName.equalsIgnoreCase(cgName))
        .findFirst()
        .orElse(null);
  }

  public static Network getByCcName(String ccName) {
    return Stream.of(values())
        .filter(network -> network.cryptoCompareName.equalsIgnoreCase(ccName))
        .findFirst()
        .orElse(null);
  }
}
