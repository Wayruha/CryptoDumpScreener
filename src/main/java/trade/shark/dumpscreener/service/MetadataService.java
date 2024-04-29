package trade.shark.dumpscreener.service;

import trade.shark.dumpscreener.domain.NetworkContract;
import trade.shark.dumpscreener.domain.Token;

//todo it should hold the metadata returned by aggregator API (coingecko?)
public class MetadataService {

  public Token resolveTokenByContract(NetworkContract contract) {
    return new Token();
  }
}
