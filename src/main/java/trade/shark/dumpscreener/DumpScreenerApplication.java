package trade.shark.dumpscreener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;


@SpringBootApplication
@EnableScheduling
public class DumpScreenerApplication {
  public static final Logger CLI_LOG = LoggerFactory.getLogger("user-display");
  public static final String APP_NAME = "DumpScreenerBot";

  public static void main(String[] args) {
    SpringApplication.run(DumpScreenerApplication.class, args);
  }

}
