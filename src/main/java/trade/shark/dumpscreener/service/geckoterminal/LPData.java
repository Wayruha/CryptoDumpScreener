package trade.shark.dumpscreener.service.geckoterminal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class LPData<T> {
  private String id;
  private String type;
  private T attributes;
}