package trade.shark.dumpscreener.service.geckoterminal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GeckoTerminalResponse<T> {
  private T data;
}
