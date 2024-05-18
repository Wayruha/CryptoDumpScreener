package trade.shark.dumpscreener.service;

import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import trade.shark.dumpscreener.config.AppProperties;
import trade.shark.dumpscreener.domain.NetworkContract;
import trade.shark.dumpscreener.domain.Token;
import trade.shark.dumpscreener.enums.Network;
import trade.shark.dumpscreener.event.DumpSignalEvent;
import trade.shark.dumpscreener.util.MathUtil;
import trade.wayruha.oneinch.service.SpotService;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
public class PriceScreenerService {
  private static final int ONEINCH_TOKEN_COUNT_THRESHOLD = 10000;

  private final MetadataService metadataService;
  private final SpotService oneInchSpotService;
  private final ApplicationEventPublisher eventPublisher;
  private final AppProperties properties;

  private final List<Map<NetworkContract, BigDecimal>> priceMaps = new LinkedList<>();
  private final Long priceMapsToMaintain;

  public PriceScreenerService(MetadataService metadataService,
                              ApplicationEventPublisher eventPublisher,
                              SpotService oneInchSpotService,
                              AppProperties properties) {
    this.metadataService = metadataService;
    this.properties = properties;
    this.eventPublisher = eventPublisher;
    this.oneInchSpotService = oneInchSpotService;
    final Long longestTimeWindow = properties.getRules().stream()
        .map(AppProperties.Rule::getTimeWindowSec)
        .max(Long::compareTo)
        .orElse(0L);
    this.priceMapsToMaintain = Math.ceilDiv(longestTimeWindow, properties.getScreeningRateSec());
  }

  private Map<NetworkContract, BigDecimal> fetchContractPrices() {
    final Map<NetworkContract, BigDecimal> priceMap = new HashMap<>();
    final Map<Network, List<NetworkContract>> tokensByNetwork = properties.getNetworks().stream()
        .collect(Collectors.toMap(Function.identity(), metadataService::getContractsByNetwork));

    tokensByNetwork.forEach((net, tokens) -> Lists.partition(tokens, ONEINCH_TOKEN_COUNT_THRESHOLD)
        .forEach(sublist -> priceMap.putAll(loadContractPrices(net, sublist))));
    return priceMap;
  }

  private Map<NetworkContract, BigDecimal> loadContractPrices(Network network, List<NetworkContract> networkContracts) {
    final Map<NetworkContract, BigDecimal> priceMap = new HashMap<>();
    final List<String> contractsAddr = networkContracts.stream()
        .map(NetworkContract::getContractAddress)
        .map(String::toLowerCase)
        .toList();

    final Map<String, BigDecimal> prices = oneInchSpotService.getTokenDollarPrices(network.getOneInchChain(), contractsAddr);

    networkContracts.forEach(c -> {
      final String contract = c.getContractAddress().toLowerCase();
      final BigDecimal price = prices.get(contract);
      if (price != null) {
        priceMap.put(c, price);
      }
    });

    return priceMap;
  }

  public void detectDumps() {
    final Map<NetworkContract, BigDecimal> currentPrices = fetchContractPrices();

    if (this.priceMaps.size() >= this.priceMapsToMaintain) {
      this.priceMaps.removeFirst();
    }
    this.priceMaps.add(currentPrices);


    final List<DumpSignalEvent> detectedEvents = properties.getRules().stream()
        .map(this::detectByRule)
        .flatMap(List::stream)
        .distinct()
        .toList();
    detectedEvents.forEach(eventPublisher::publishEvent);
  }

  private List<DumpSignalEvent> detectByRule(AppProperties.Rule rule) {
    final List<DumpSignalEvent> events = new ArrayList<>();
    final Map<NetworkContract, BigDecimal> old = getOldPriceMapForRule(rule);
    final Map<NetworkContract, BigDecimal> current = priceMaps.getLast();

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
            Duration.ofMillis(rule.getTimeWindowSec()));
        events.add(event);
      }
    });
    return events;
  }

  public Map<NetworkContract, BigDecimal> getOldPriceMapForRule(AppProperties.Rule rule) {
    final int timeWindowIndex = priceMaps.size() - (int) Math.ceilDiv(rule.getTimeWindowSec(), properties.getScreeningRateSec());
    int snapshotIndex = Math.max(0, timeWindowIndex);
    return priceMaps.get(Math.min(snapshotIndex, priceMaps.size() - 1));
  }
}
