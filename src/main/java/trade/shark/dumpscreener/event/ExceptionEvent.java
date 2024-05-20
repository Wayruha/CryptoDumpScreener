package trade.shark.dumpscreener.event;

import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
public class ExceptionEvent {
  public static final String ACTION_METADATA_UPDATE = "metadata-update";
  public static final String ACTION_DEXSCREENER_METADATA_UPDATE = "metadata-update-dexscreener";

  private final String action;
  private final Exception exception;
}
