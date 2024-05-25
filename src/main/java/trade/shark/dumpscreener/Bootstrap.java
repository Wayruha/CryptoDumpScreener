package trade.shark.dumpscreener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import trade.shark.dumpscreener.event.ExceptionEvent;
import trade.shark.dumpscreener.service.MetadataService;
import trade.shark.dumpscreener.service.PriceScreenerService;
import trade.shark.dumpscreener.service.dexscreener.DexScreenerClientException;

import java.util.concurrent.TimeUnit;

@Component
@Slf4j
@RequiredArgsConstructor
public class Bootstrap {
  private final MetadataService metadataService;
  private final PriceScreenerService priceScreenerService;
  private final ApplicationEventPublisher eventPublisher;

  @Async
  @Scheduled(fixedDelayString = "${screener.metadataUpdateRateSec}", timeUnit = TimeUnit.SECONDS)
  public void updateMetadata() {
    log.debug("Updating metadata...");
    try {
      DumpScreenerApplication.CLI_LOG.info("Metadata is updating...");
      metadataService.updateMetadata();
    } catch (Exception ex) {
      log.error("Exception during metadata update", ex);
      eventPublisher.publishEvent(new ExceptionEvent(ExceptionEvent.ACTION_METADATA_UPDATE, ex));
    }
  }

  @Async
  @Scheduled(fixedDelayString = "${screener.screeningRateSec}", initialDelayString = "${screener.initialDelaySec}", timeUnit = TimeUnit.SECONDS)
  public void startScreening() {
    try {
      if (metadataService.getLastUpdate() == null) {
        log.warn("Metadata is not initialized yet, skip this round.");
        return;
      }
      priceScreenerService.detectDumps();
    } catch (Exception ex) {
      log.error("Execution error", ex);
    }
  }
}
