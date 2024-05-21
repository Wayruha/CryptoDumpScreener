package trade.shark.dumpscreener.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import trade.shark.dumpscreener.domain.DexLiquidityPool;
import trade.shark.dumpscreener.domain.Token;
import trade.shark.dumpscreener.domain.TradePair;
import trade.shark.dumpscreener.enums.Network;
import trade.shark.dumpscreener.event.DumpSignalEvent;
import trade.shark.dumpscreener.exception.NotificationException;
import trade.shark.dumpscreener.util.MathUtil;

import java.util.Optional;

import static java.lang.String.valueOf;
import static java.util.Optional.ofNullable;
import static trade.shark.dumpscreener.util.MathUtil.alternativeMoneyFormat;
import static trade.shark.dumpscreener.util.MathUtil.calculateAgeInDays;
import static trade.shark.dumpscreener.util.MathUtil.formatPrice;
import static trade.shark.dumpscreener.util.MathUtil.getFormattedSpread;

@Service
@Slf4j
public class TgNotificationService {
  private static final String DEX_1inch = "1inch";

  private static final String SIGNAL_CEXES_TEMPLATE = "  ${exchange}: `${price}, ${spread}%`\n";
  private static final String MSG_DIVIDER = "---------------------\n";
  private static final String SIGNAL_MSG_TEMPLATE = """
      *${symbol}/${priceChangePercent}%*
              
      Address: [${tokenAddress}](${tokenURL})
      Network: `${tokenNetwork}`
      Name: *${tokenName}*
      TimeWindow: ${detectionTimeWindow} sec
      MarketCap: ${tokenMarketCap}$
      Volume24H: ${volume24H}$
      Age: ${tokenAge}
      Dex:
        Market: ${dexMarket}
        Pool: ${dexPoolPair}
        Liquidity: ${poolLiquidity}$
        Price: ${dexPrice}$
      """;

  private final TelegramClient telegram;

  public TgNotificationService(Optional<TelegramClient> telegram) {
    this.telegram = telegram.orElse(null);
    telegram.ifPresent(t -> log.info("NotificationService is enabled: telegram"));
  }

  public void sendNotifications(DumpSignalEvent event) {
    final String text = toTgDisplayText(event);
    sendNotification(text);
  }

  public void sendNotification(String text) {
    try {
      telegram.sendNotification(text, true);
    } catch (Exception ex) {
      throw new NotificationException(ex);
    }
  }

  public static String toTgDisplayText(DumpSignalEvent event) {
    final Token token = event.getToken();
    final Network network = event.getNetwork();
    final StringBuilder bldr = new StringBuilder(MSG_DIVIDER);
    final Optional<DexLiquidityPool> dexLP = ofNullable(token.getDexLiquidityPool());
    bldr.append(SIGNAL_MSG_TEMPLATE.replace("${symbol}", token.getSymbol().toUpperCase())
        .replace("${priceChangePercent}", getFormattedSpread(event.getChangePercentage()))
        .replace("${tokenAddress}", token.getContractAddress(network))
        .replace("${tokenURL}", buildTokenUrl(token, network))
        .replace("${tokenNetwork}", network.toString())
        .replace("${tokenName}", token.getName())
        .replace("${detectionTimeWindow}", valueOf(event.getMonitoredTimeWindow().getSeconds()))
        .replace("${volume24H}", alternativeMoneyFormat(token.getUsdVolume24H()))
        .replace("${tokenMarketCap}", alternativeMoneyFormat(token.getMarketCap()))
        .replace("${tokenAge}", token.getDeploymentTime() != null ? calculateAgeInDays(token.getDeploymentTime()) + " days" : "--")
        .replace("${dexMarket}", dexLP.map(DexLiquidityPool::getDexName).orElse(DEX_1inch))
        .replace("${dexPoolPair}", dexLP.map(DexLiquidityPool::getLiquidityPoolPair).map(TradePair::toString).orElse("--"))
        .replace("${poolLiquidity}", dexLP.map(DexLiquidityPool::getPoolLiquidityUsd).map(MathUtil::alternativeMoneyFormat).orElse("-"))
        .replace("${dexPrice}", formatPrice(event.getCurrentPrice())));
    if (!event.getCexOptions().isEmpty()) {
      bldr.append("__Spread on CEX__:\n");
    }
    event.getCexOptions().values().forEach(spread -> {
      bldr.append(SIGNAL_CEXES_TEMPLATE
          .replace("${exchange}", spread.getSellExchange().toString())
          .replace("${price}", formatPrice(spread.getCurrentPrice()))
          .replace("${spread}", getFormattedSpread(spread.getSpreadPercentage())));
    });
    bldr.append(MSG_DIVIDER);
    return bldr.toString();
  }

  private static String buildTokenUrl(Token token, Network network){
    return "https://dexscreener.com/" + network.toString().toLowerCase() + "/" + token.getContractAddress(network);
  }
}
