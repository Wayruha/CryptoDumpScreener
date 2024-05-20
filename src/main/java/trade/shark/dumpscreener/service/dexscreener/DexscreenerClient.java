package trade.shark.dumpscreener.service.dexscreener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DexscreenerClient {
  private final WebClient webClient;

  public TokensResponse getMetadata(List<String> contracts) {
    log.debug("Fetching metadata for contracts: {}", contracts.size());
    final Mono<TokensResponse> responseFlux = webClient.get()
        .uri("https://api.dexscreener.com/latest/dex/tokens/" + String.join(",", contracts))
        .retrieve()
        .bodyToMono(TokensResponse.class);

    return  responseFlux.block();
  }
}
