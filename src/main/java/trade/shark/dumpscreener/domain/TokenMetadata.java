package trade.shark.dumpscreener.domain;

public interface TokenMetadata {
  String getSymbol();

  String getName();

  Long deploymentTime();

  NetworkContract getIdentityContract();
}