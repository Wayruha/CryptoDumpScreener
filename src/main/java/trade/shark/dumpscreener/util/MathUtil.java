package trade.shark.dumpscreener.util;

import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Locale;

import static java.lang.String.valueOf;
import static java.util.Objects.isNull;
import static org.apache.commons.lang3.math.NumberUtils.createBigDecimal;

public class MathUtil {
  private static final String availableMoneySuffixes = "kMBT";
  public static final BigDecimal PERCENTAGE_100 = new BigDecimal("100");
  public static final int COIN_QTY_DECIMAL_PRECISION = 4;
  public static final MathContext COIN_SAFE_MC = new MathContext(8, RoundingMode.HALF_DOWN);
  public static final MathContext PRICE_MC = new MathContext(8, RoundingMode.HALF_DOWN);
  public static final int MILLIS_IN_DAY = 60 * 60 * 24;

  public static BigDecimal calculateTotalCost(BigDecimal amount, BigDecimal price) {
    if (isNull(amount) || isNull(price)) return null;
    return amount.multiply(price);
  }

  public static BigDecimal calculateQty(BigDecimal totalAmount, BigDecimal price) {
    if (isNull(totalAmount)) return BigDecimal.ZERO;
    return totalAmount.divide(price, COIN_SAFE_MC);
  }

  public static BigDecimal calculatePrice(BigDecimal amount, BigDecimal qty) {
    if (amount == null) return null;
    return amount.divide(qty, COIN_SAFE_MC);
  }

  public static String formatCoinQty(BigDecimal amount) {
    if (amount == null) return "-";
    return amount.setScale(COIN_QTY_DECIMAL_PRECISION, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
  }

  public static String formatDollarAmount(BigDecimal amount) {
    if (amount == null) return "-";
    return amount.setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
  }

  public static BigDecimal calculateFraction(BigDecimal freeBalance, BigDecimal fraction) {
    if (isNull(freeBalance)) return BigDecimal.ZERO;
    return freeBalance.multiply(fraction.divide(PERCENTAGE_100, COIN_SAFE_MC));
  }

  public static String formatPrice(BigDecimal num) {
    if (isNull(num)) return "";
    return num.round(PRICE_MC).stripTrailingZeros().toPlainString();
  }

  public static String getFormattedSpread(BigDecimal spread) {
    if (isNull(spread)) return "-";
    return spread.setScale(2, RoundingMode.HALF_UP).toPlainString();
  }

  public static BigDecimal calculateSpread(BigDecimal buyPrice, BigDecimal sellPrice) {
    return PERCENTAGE_100.divide(buyPrice, COIN_SAFE_MC)
        .multiply(sellPrice, COIN_SAFE_MC)
        .subtract(PERCENTAGE_100);
  }

  public static BigDecimal calculateDeviation(BigDecimal n1, BigDecimal n2) {
    final BigDecimal max = n1.max(n2);
    final BigDecimal min = n1.min(n2);
    return max.subtract(min).divide(max, 3, RoundingMode.HALF_UP);
  }

  public static BigDecimal toBigDecimal(String num) {
    if (StringUtils.isBlank(num)) return null;
    return createBigDecimal(num);
  }

  public static int calculateAgeInDays(Long timestamp) {
    return (int) ((System.currentTimeMillis() / 1000 - timestamp) / MILLIS_IN_DAY);
  }

  public static String alternativeMoneyFormat(BigDecimal _number) {
    if (_number == null || _number.signum() == 0) return "-";
    double number = _number.doubleValue();
    if (number < 1000) return valueOf(number);
    int exp = Math.min((int) (Math.log(number) / Math.log(1000)), availableMoneySuffixes.length());
    char pre = availableMoneySuffixes.charAt(exp - 1);
    return String.format(Locale.US, "%.1f%c", number / Math.pow(1000, exp), pre);
  }
}
