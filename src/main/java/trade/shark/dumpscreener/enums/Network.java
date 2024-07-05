package trade.shark.dumpscreener.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import trade.wayruha.oneinch.Chain;

import java.util.stream.Stream;

@AllArgsConstructor
@Getter
public enum Network {
  ETHEREUM("ethereum", "ETH", Chain.ETHEREUM, "ethereum"),
  ARBITRUM("arbitrum", "ARB", Chain.ARBITRUM, "arbitrum"),
  SOLANA("solana", "SOL", null, "solana"),
  AVAX("avalanche", "AVAX", Chain.AVALANCHE, "avalanche"),
  POLYGON("polygon-pos", "MATIC", Chain.POLYGON, "polygon"),
  BSC("binance-smart-chain", "BNB", Chain.BSC, "bsc"),
  BASE("base", "BASECHAIN", Chain.BASE, "base");

  final String coingeckoName;
  final String cryptoCompareName;
  final Chain oneInchChain;
  final String dexScreenerName;

  public static Network getByCoingeckoName(String cgName) {
    return Stream.of(values())
        .filter(network -> network.coingeckoName.equalsIgnoreCase(cgName))
        .findFirst()
        .orElse(null);
  }

  public static Network getByCryptocompareName(String ccName) {
    return Stream.of(values())
        .filter(network -> network.cryptoCompareName.equalsIgnoreCase(ccName))
        .findFirst()
        .orElse(null);
  }

  public static Network getByOneInchChain(Chain chain) {
    return Stream.of(values())
        .filter(network -> network.oneInchChain == chain)
        .findFirst()
        .orElse(null);
  }

  public static Network getByDexScreenerName(String dexScreenerName) {
    return Stream.of(values())
        .filter(network -> network.dexScreenerName.equalsIgnoreCase(dexScreenerName))
        .findFirst()
        .orElse(null);
  }
}
