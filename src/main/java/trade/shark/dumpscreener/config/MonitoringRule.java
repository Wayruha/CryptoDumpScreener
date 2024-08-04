package trade.shark.dumpscreener.config;

import lombok.Data;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;

@Data
public class MonitoringRule implements Comparable<MonitoringRule> {
  private BigDecimal triggerPercentage;
  private Long timeWindowSec;

  @Override
  public int compareTo(@NotNull MonitoringRule rule) {
    return timeWindowSec.compareTo(rule.getTimeWindowSec());
  }
}
