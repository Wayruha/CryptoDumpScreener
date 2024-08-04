package trade.shark.dumpscreener.service;

import com.google.common.util.concurrent.RateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import trade.shark.dumpscreener.domain.NetworkContract;
import trade.shark.dumpscreener.service.geckoterminal.GeckoTerminalResponse;
import trade.shark.dumpscreener.service.geckoterminal.LPData;
import trade.shark.dumpscreener.service.geckoterminal.LPTransaction;

import java.util.List;

@RequiredArgsConstructor
@Slf4j
@Component
public class GeckoTerminalService {
  private static final ParameterizedTypeReference<GeckoTerminalResponse<List<LPData<LPTransaction>>>> LP_TRADES_DTO
      = new ParameterizedTypeReference<>() {
  };
  private static final String GECKO_API_HOST = "https://api.geckoterminal.com";
  private static final String TRADES_API_PATH_PATTERN = "/api/v2/networks/%s/pools/%s/trades";
  private static final int REQUESTS_PER_SECOND = 1;
  private final RateLimiter rateLimiter = RateLimiter.create(REQUESTS_PER_SECOND);
  private final RestTemplate restTemplate;

  public List<LPTransaction> loadPoolTransactions(NetworkContract pool) {
    try {
      rateLimiter.acquire();
      final String poolAddress = formatContract(pool);
      final String fullUrl = GECKO_API_HOST +
          TRADES_API_PATH_PATTERN.formatted(pool.getNetwork().getGeckoTerminalName(), poolAddress);

      final ResponseEntity<GeckoTerminalResponse<List<LPData<LPTransaction>>>> response =
          restTemplate.exchange(fullUrl, HttpMethod.GET, null, LP_TRADES_DTO);
      final GeckoTerminalResponse<List<LPData<LPTransaction>>> body = response.getBody();
      if (body == null) throw new IllegalStateException("Returned body is null");
      return body.getData().stream().map(LPData::getAttributes).toList();
    } catch (Exception ex) {
      throw new RuntimeException("Can't get trades from GeckoTerminal for " + pool);
    }
  }

  private static String formatContract(NetworkContract pool) {
    return switch (pool.getNetwork()) {
      case SOLANA -> pool.getContractAddress();
      default -> pool.getContractAddress().toLowerCase();
    };
  }
}
