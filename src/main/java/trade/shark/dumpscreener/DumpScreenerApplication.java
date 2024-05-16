package trade.shark.dumpscreener;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DumpScreenerApplication {
  public static final String APP_NAME = "DumpScreenerBot";

  public static void main(String[] args) {
    SpringApplication.run(DumpScreenerApplication.class, args);
  }

}
