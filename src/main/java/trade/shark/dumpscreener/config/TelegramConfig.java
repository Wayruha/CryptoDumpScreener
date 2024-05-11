package trade.shark.dumpscreener.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "telegram")
@AllArgsConstructor
@NoArgsConstructor
public class TelegramConfig {
    private String apiToken;
    private Long notificationChatId;
    private Long controlChatId;
}
