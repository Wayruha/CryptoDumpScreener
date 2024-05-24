package trade.shark.dumpscreener.service;

import com.google.common.collect.Lists;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import trade.shark.dumpscreener.domain.NetworkContract;
import trade.shark.dumpscreener.enums.Network;
import trade.wayruha.oneinch.service.SpotService;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class OneInchPriceProvider implements PriceProvider {
  public static final int ONEINCH_TOKEN_COUNT_THRESHOLD = 10000;
  private final SpotService oneInchSpotService;

  @Override
  public Map<NetworkContract, BigDecimal> loadPrices(List<NetworkContract> contracts) {
    final Map<NetworkContract, BigDecimal> priceMap = new HashMap<>();

    //group contracts by network
    final Map<Network, List<NetworkContract>> contractsByNetwork = contracts.stream()
        .collect(Collectors.groupingBy(NetworkContract::getNetwork));
    contractsByNetwork.forEach((net, tokens) -> Lists.partition(tokens, ONEINCH_TOKEN_COUNT_THRESHOLD)
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
}
