package trade.shark.dumpscreener.domain;

import lombok.Getter;
import trade.shark.dumpscreener.enums.Network;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Objects.requireNonNull;

@Getter
public class NetworkContract {
  private static final Map<String, NetworkContract> CONTRACTS_CACHE = new ConcurrentHashMap<>();

  private final String contractAddress;
  private final Network network;

  private NetworkContract(String contractAddress, Network network) {
    this.contractAddress = contractAddress;
    this.network = network;
  }

  public static NetworkContract of(String contractAddress, Network network) {
    return getInstance(contractAddress, network);
  }

  private static NetworkContract getInstance(String contractAddress, Network network) {
    requireNonNull(contractAddress);
    requireNonNull(network);
    return CONTRACTS_CACHE.computeIfAbsent(contractAddress + network, key -> new NetworkContract(contractAddress, network));
  }
}
