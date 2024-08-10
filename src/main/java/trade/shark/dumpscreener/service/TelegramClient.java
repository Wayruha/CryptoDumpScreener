package trade.shark.dumpscreener.service;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import trade.shark.dumpscreener.DumpScreenerApplication;
import trade.shark.dumpscreener.config.TelegramConfig;

import static java.util.Objects.requireNonNull;

@ConditionalOnProperty(prefix = "telegram", name = "apiToken")
@Component
@Slf4j
public class TelegramClient extends TelegramLongPollingBot {
  private static final String DEFAULT_BOT_NAME = DumpScreenerApplication.APP_NAME;

  private static TelegramBotsApi botsApi;
  private final TelegramConfig config;

  @SneakyThrows
  public TelegramClient(TelegramConfig config) {
    requireNonNull(config.getApiToken());
    requireNonNull(config.getNotificationChatId());
    this.config = config;
  }

  public boolean sendNotification(String text, boolean markdownMode) {
    if (text == null || text.isBlank()) return false;
    final SendMessage action = new SendMessage(String.valueOf(config.getNotificationChatId()), text);
    action.setParseMode(markdownMode ? ParseMode.MARKDOWN : ParseMode.HTML);
    action.setDisableWebPagePreview(true);
    return processAction(action);
  }

  @Override
  public void onUpdateReceived(Update update) {
  }

  private boolean processAction(SendMessage action) {
    try {
      final Message response = execute(action);
      log.debug("Telegram sent: {}", action);
      return response.getMessageId() != null && response.getMessageId() > 0;
    } catch (Exception ex) {
      log.error("Can't send Telegram message: {}.", action, ex);
      return false;
    }
  }

  @Override
  public String getBotUsername() {
    return DEFAULT_BOT_NAME;
  }

  @Override
  public String getBotToken() {
    return config.getApiToken();
  }
}
