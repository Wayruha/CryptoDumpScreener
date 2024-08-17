package trade.shark.dumpscreener.domain;

import lombok.Getter;
import lombok.ToString;
import trade.shark.dumpscreener.enums.Network;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Objects.requireNonNull;

@Getter
@ToString
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

  public String getContractAddress() {
    if (contractAddress == null) {
      return null;
    }
    return network == Network.SOLANA ? contractAddress : contractAddress.toUpperCase();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof NetworkContract that)) return false;

    if (!Objects.equals(getContractAddress(), that.getContractAddress()))
      return false;
    return network == that.network;
  }

  @Override
  public int hashCode() {
    int result = getContractAddress() != null ? getContractAddress().hashCode() : 0;
    result = 31 * result + (network != null ? network.hashCode() : 0);
    return result;
  }
}
