package trade.shark.dumpscreener.event;

import lombok.Data;
import trade.shark.dumpscreener.domain.Token;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class MetadataRefreshedEvent {
  private LocalDateTime timestamp;
  private List<Token> supportedTokens;
  private Duration timeSpend;

  public MetadataRefreshedEvent(List<Token> supportedTokens, Duration timeSpend) {
    this.timestamp = LocalDateTime.now();
    this.supportedTokens = supportedTokens;
    this.timeSpend = timeSpend;
  }
}
