package trade.shark.dumpscreener;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DumpScreenerApplication {
  public static final String APP_NAME = "DumpScreenerBot";

  public static void main(String[] args) {
    SpringApplication.run(DumpScreenerApplication.class, args);
  }

}
