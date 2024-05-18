package trade.shark.dumpscreener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import trade.shark.dumpscreener.service.MetadataService;
import trade.shark.dumpscreener.service.PriceScreenerService;

import java.util.concurrent.TimeUnit;

@Component
@Slf4j
@RequiredArgsConstructor
public class Bootstrap {
  private final MetadataService metadataService;
  private final PriceScreenerService priceScreenerService;

  @Async
  @Scheduled(fixedDelayString = "${screener.metadataUpdateRateSec}", timeUnit = TimeUnit.SECONDS)
  public void refreshCache() {
    log.debug("refreshing token cache");
    try {
      metadataService.fetchCoinsMetadata();
    } catch (Exception ex) {
      log.error("Execution error", ex);
    }
  }

  @Async
  @Scheduled(fixedDelayString = "${screener.screeningRateSec}", initialDelayString = "${screener.initialDelaySec}", timeUnit = TimeUnit.SECONDS)
  public void startScreening() {
    log.debug("starting screening");
    try {
      if(metadataService.getLastUpdate() == null) {
        log.warn("Metadata is not initialized yet, skip this round.");
        return;
      }
      priceScreenerService.detectDumps();
    } catch (Exception ex) {
      log.error("Execution error", ex);
    }
  }
}
