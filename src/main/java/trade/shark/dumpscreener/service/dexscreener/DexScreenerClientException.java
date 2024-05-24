package trade.shark.dumpscreener.service.dexscreener;

public class DexScreenerClientException extends RuntimeException{
  public DexScreenerClientException(String message) {
    super(message);
  }

  public DexScreenerClientException(String message, Throwable cause) {
    super(message, cause);
  }
}
