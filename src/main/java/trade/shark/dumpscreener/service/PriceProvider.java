package trade.shark.dumpscreener.service;

import trade.shark.dumpscreener.domain.NetworkContract;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface PriceProvider {

  Map<NetworkContract, BigDecimal> loadPrices(List<NetworkContract> contracts);
}
