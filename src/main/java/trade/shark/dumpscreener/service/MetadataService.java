package trade.shark.dumpscreener.service;

import trade.shark.dumpscreener.domain.NetworkContract;
import trade.shark.dumpscreener.domain.Token;

import java.util.List;
//feel free to remove this class/components if it is not needed

//todo it should hold the metadata returned by aggregator API (coingecko?)
public class MetadataService {
  //todo cryptocompare metadata holder: Map<NetworkContract, CCAssetInfo> if neeeded
  //todo coingecko metadata holder: Map<NetworkContract, CGAssetInfo> if needed

  public List<Token> loadSupportedTokens(){
    //todo load the supported tokens from the coingecko api
    // do we need to find the
    return null;
  }

  public Token resolveTokenByContract(NetworkContract contract) {
    return new Token();
  }

  //CryptoCompare Asset Metadata
  public static class CCAssetInfo{
    private String id;
    private String symbol;
  }

  // CoinGecko Asset Metadata
  public static class CGAssetInfo{
    private String id;
    private String symbol;
  }
}
