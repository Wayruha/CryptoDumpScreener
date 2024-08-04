package trade.shark.dumpscreener.service.geckoterminal;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class LPTransaction {
  @JsonAlias("block_number")
  private Long blockNumber;

  @JsonAlias("tx_hash")
  private String txHash;

  @JsonAlias("tx_from_address")
  private String txFromAddress;

  @JsonAlias("from_token_amount")
  private BigDecimal fromTokenAmount;

  @JsonAlias("to_token_amount")
  private BigDecimal toTokenAmount;

  @JsonAlias("price_from_in_currency_token")
  private BigDecimal priceFromInCurrencyToken;

  @JsonAlias("price_to_in_currency_token")
  private BigDecimal priceToInCurrencyToken;

  @JsonAlias("price_from_in_usd")
  private BigDecimal priceFromInUsd;

  @JsonAlias("price_to_in_usd")
  private BigDecimal priceToInUsd;

  @JsonAlias("block_timestamp")
  private ZonedDateTime blockTimestamp;

  private TradeSide kind;

  @JsonAlias("volume_in_usd")
  private BigDecimal volumeInUsd;

  @JsonAlias("from_token_address")
  private String fromTokenAddress;

  @JsonAlias("to_token_address")
  private String toTokenAddress;

  public enum TradeSide {
    buy, sell;
  }
}
