package trade.shark.dumpscreener.service;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import trade.shark.dumpscreener.config.AppProperties;
import trade.shark.dumpscreener.config.MonitoringRule;
import trade.shark.dumpscreener.domain.NetworkContract;
import trade.shark.dumpscreener.domain.Token;
import trade.shark.dumpscreener.event.DumpSignalEvent;
import trade.shark.dumpscreener.util.MathUtil;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

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
        .map(MonitoringRule::getTimeWindowSec)
        .max(Long::compareTo)
        .orElse(0L);
    this.priceMapsToMaintain = Math.ceilDiv(longestTimeWindow, properties.getScreeningRateSec()) + 1;
  }

  /**
   * Method for fetching token prices and notifying user on dump detection
   */
  public void detectDumps() {
    if (this.priceMaps.size() >= this.priceMapsToMaintain) {
      this.priceMaps.removeFirst();
    }
    final PriceSnapshot snapshot = new PriceSnapshot(LocalDateTime.now(), metadataService.getTokens().size());
    this.priceMaps.add(snapshot);

    final List<NetworkContract> primaryTokenContracts = getPrimaryTokenContracts(metadataService.getTokens());
    final Map<NetworkContract, BigDecimal> currentPrices = priceProvider.loadPrices(primaryTokenContracts);
    snapshot.getPrices().putAll(currentPrices);

    final List<DumpSignalEvent> detectedEvents = properties.getRules().stream()
        .map(this::detectByRule)
        .flatMap(List::stream)
        .toList();
    final Collection<DumpSignalEvent> distinctEvents = detectedEvents.stream()
        .collect(Collectors.toMap(
            DumpSignalEvent::getToken,
            Function.identity(),
            (e1, e2) -> e1.getDetectedRule().compareTo(e2.getDetectedRule()) >= 0 ? e1 : e2))
        .values();
    distinctEvents.forEach(eventPublisher::publishEvent);
  }

  /**
   * Analyzes price snapshots for specified rule
   *
   * @param rule dump detection rule
   */
  private List<DumpSignalEvent> detectByRule(MonitoringRule rule) {
    if (priceMaps.size() < 2) {
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
        //TODO handle tokens duplication with different networks
        final Token token = metadataService.getTokenByContract(contract);
        final DumpSignalEvent event = new DumpSignalEvent(
            token,
            contract.getNetwork(),
            currentPrice,
            currentPrice.subtract(oldPrice),
            changePercent,
            rule);
        events.add(event);
      }
    });
    return events;
  }

  /**
   * Retrieves price snapshot that corresponds to the beginning of a period defined by specified rule
   *
   * @param rule dump detection rule
   */
  private Map<NetworkContract, BigDecimal> getOldPriceMapForRule(MonitoringRule rule) {
    if (priceMaps.isEmpty()) {
      return new HashMap<>();
    }
    final int timeWindowIndex = priceMaps.size() - 1 - (int) Math.ceilDiv(rule.getTimeWindowSec(), properties.getScreeningRateSec());
    final int boundedIndex = Math.min(Math.max(0, timeWindowIndex), priceMaps.size() - 1);
    return priceMaps.get(boundedIndex).getPrices();
  }

  public List<NetworkContract> getPrimaryTokenContracts(List<Token> tokens) {
    return tokens.stream()
        .map(Token::getPrimaryContract)
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
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
