package trade.shark.dumpscreener.domain;

public interface TokenMetadata {
  String getSymbol();

  String getName();

  NetworkContract getIdentityContract();
}