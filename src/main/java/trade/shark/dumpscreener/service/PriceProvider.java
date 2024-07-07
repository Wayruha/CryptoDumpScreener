package trade.shark.dumpscreener.service;

import trade.shark.dumpscreener.domain.NetworkContract;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface PriceProvider {

  /**
   * Extracts price snapshot for given contracts
   *
   * @param contracts network contacts to extract price snapshot for
   */
  Map<NetworkContract, BigDecimal> loadPrices(List<NetworkContract> contracts);
}
