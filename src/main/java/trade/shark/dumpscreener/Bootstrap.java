package trade.shark.dumpscreener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import trade.shark.dumpscreener.service.MetadataService;
import trade.shark.dumpscreener.service.PriceScreenerService;

@Component
@Slf4j
@RequiredArgsConstructor
public class Bootstrap {
  private final MetadataService metadataService;
  private final PriceScreenerService priceScreenerService;

  @Async
  @Scheduled(fixedDelayString = "${screener.detectRate}", initialDelay = 0L)
  public void refreshCache() {
    log.debug("refreshing token cache");
    try {
      metadataService.fetchCoinsMetadata();
    } catch (Exception ex) {
      log.error("Execution error", ex);
    }
  }

  @Async
  @Scheduled(fixedDelayString = "${screener.screeningRate}", initialDelay = 0L)
  public void startScreening() {
    log.debug("starting screening");
    try {
      priceScreenerService.detectDumps();
    } catch (Exception ex) {
      log.error("Execution error", ex);
    }
  }
}
