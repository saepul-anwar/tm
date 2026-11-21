package com.studenttask.telegram.runner;

import com.studenttask.config.AppProperties;
import com.studenttask.telegram.client.TelegramClient;
import com.studenttask.telegram.client.TelegramException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Registers the public webhook with Telegram at startup, only when {@code app.telegram.mode=webhook} and runners are
 * enabled. A missing or non-HTTPS {@code app.telegram.public-base-url} is a configuration error and aborts startup
 * (Telegram only delivers to HTTPS). A failed call to Telegram is logged (kind only) and does not stop the app.
 */
@Component
public class WebhookRegistrar {

    static final String WEBHOOK_PATH = "/api/telegram/webhook";
    private static final Logger log = LoggerFactory.getLogger(WebhookRegistrar.class);

    private final AppProperties props;
    private final TelegramClient client;

    public WebhookRegistrar(AppProperties props, TelegramClient client) {
        this.props = props;
        this.client = client;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void register() {
        AppProperties.Telegram telegram = props.telegram();
        if (telegram == null || telegram.mode() != AppProperties.Telegram.Mode.WEBHOOK || !telegram.runnersEnabled()
                || telegram.botToken() == null || telegram.botToken().isBlank()) {
            return;
        }
        String base = telegram.publicBaseUrl() == null ? "" : telegram.publicBaseUrl().trim();
        if (!base.startsWith("https://")) {
            throw new IllegalStateException(
                    "app.telegram.public-base-url (TELEGRAM_PUBLIC_BASE_URL) must be an https:// URL in webhook mode");
        }
        String url = (base.endsWith("/") ? base.substring(0, base.length() - 1) : base) + WEBHOOK_PATH;
        try {
            client.setWebhook(url, telegram.webhookSecret());
            log.info("Telegram webhook registered");
        } catch (TelegramException e) {
            log.warn("Telegram webhook registration failed ({})", e.kind());
        }
    }
}
