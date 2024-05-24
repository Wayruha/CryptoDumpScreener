package trade.shark.dumpscreener.service;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import trade.shark.dumpscreener.config.AppProperties;
import trade.shark.dumpscreener.domain.NetworkContract;
import trade.shark.dumpscreener.domain.Token;
import trade.shark.dumpscreener.event.DumpSignalEvent;
import trade.shark.dumpscreener.util.MathUtil;
import trade.wayruha.oneinch.service.SpotService;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class PriceScreenerService {
  private static final int ONEINCH_TOKEN_COUNT_THRESHOLD = 10000;

  private final MetadataService metadataService;
  private final ApplicationEventPublisher eventPublisher;
  private final AppProperties properties;

  private final List<PriceSnapshot> priceMaps = new LinkedList<>();
  private final Long priceMapsToMaintain;
  private final PriceProvider priceProvider;

  public PriceScreenerService(MetadataService metadataService,
                              ApplicationEventPublisher eventPublisher,
                              AppProperties properties,
                              PriceProvider priceProvider) {
    this.metadataService = metadataService;
    this.properties = properties;
    this.eventPublisher = eventPublisher;
    this.priceProvider = priceProvider;
    final Long longestTimeWindow = properties.getRules().stream()
        .map(AppProperties.Rule::getTimeWindowSec)
        .max(Long::compareTo)
        .orElse(0L);
    this.priceMapsToMaintain = Math.ceilDiv(longestTimeWindow, properties.getScreeningRateSec());
  }

  public void detectDumps() {
    if (this.priceMaps.size() >= this.priceMapsToMaintain) {
      this.priceMaps.removeFirst();
    }
    final PriceSnapshot snapshot = new PriceSnapshot(LocalDateTime.now(), metadataService.getTokens().size());
    this.priceMaps.add(snapshot);

    final Map<NetworkContract, BigDecimal> currentPrices = priceProvider.loadPrices(metadataService.getTokenContracts());
    snapshot.getPrices().putAll(currentPrices);

    final List<DumpSignalEvent> detectedEvents = properties.getRules().stream()
        .map(this::detectByRule)
        .flatMap(List::stream)
        .distinct()
        .toList();
    detectedEvents.forEach(eventPublisher::publishEvent);
  }

  private List<DumpSignalEvent> detectByRule(AppProperties.Rule rule) {
    if (priceMaps.isEmpty()) {
      return List.of();
    }
    final List<DumpSignalEvent> events = new ArrayList<>();
    final Map<NetworkContract, BigDecimal> old = getOldPriceMapForRule(rule);
    final Map<NetworkContract, BigDecimal> current = priceMaps.getLast().getPrices();

    old.keySet().forEach(contract -> {
      final BigDecimal oldPrice = old.get(contract);
      final BigDecimal currentPrice = current.get(contract);
      if (oldPrice == null || currentPrice == null || oldPrice.signum() == 0 || currentPrice.signum() == 0) return;

      final BigDecimal changePercent = MathUtil.calculateSpread(oldPrice, currentPrice);
      if (changePercent.abs().compareTo(rule.getTriggerPercentage()) >= 0) {
        final Token token = metadataService.getTokenByContract(contract);
        final DumpSignalEvent event = new DumpSignalEvent(
            token,
            contract.getNetwork(),
            currentPrice,
            currentPrice.subtract(oldPrice),
            changePercent,
            Duration.ofSeconds(rule.getTimeWindowSec()));
        events.add(event);
      }
    });
    return events;
  }

  private Map<NetworkContract, BigDecimal> getOldPriceMapForRule(AppProperties.Rule rule) {
    if (priceMaps.isEmpty()) {
      return new HashMap<>();
    }
    final int timeWindowIndex = priceMaps.size() - (int) Math.ceilDiv(rule.getTimeWindowSec(), properties.getScreeningRateSec());
    int snapshotIndex = Math.max(0, timeWindowIndex);
    return priceMaps.get(Math.min(snapshotIndex, priceMaps.size() - 1)).getPrices();
  }

  @Getter
  private static class PriceSnapshot {
    private final LocalDateTime timestamp;
    private final Map<NetworkContract, BigDecimal> prices;

    public PriceSnapshot(LocalDateTime timestamp, int initialCapacity) {
      this.timestamp = timestamp;
      this.prices = new HashMap<>(initialCapacity);
    }
  }
}
