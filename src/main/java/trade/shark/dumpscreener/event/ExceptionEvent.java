package trade.shark.dumpscreener.event;

import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
public class ExceptionEvent {
  private final String action;
  private final Exception exception;
}
