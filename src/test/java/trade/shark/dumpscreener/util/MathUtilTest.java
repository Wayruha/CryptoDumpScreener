package trade.shark.dumpscreener.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static trade.shark.dumpscreener.util.MathUtil.alternativeMoneyFormat;

class MathUtilTest {

  @Test
  void test_alternativeMoneyFormat() {
    assertEquals("765.0", alternativeMoneyFormat(new BigDecimal(765)));
    assertEquals("1.3k", alternativeMoneyFormat(new BigDecimal(1325)));
    assertEquals("123.0k", alternativeMoneyFormat(new BigDecimal(123000)));
    assertEquals("4.4M", alternativeMoneyFormat(new BigDecimal(4350000)));
    assertEquals("4.4B", alternativeMoneyFormat(new BigDecimal("4350000000")));
  }
}