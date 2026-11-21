package com.studenttask.telegram.runner;

import com.studenttask.config.AppProperties;
import com.studenttask.telegram.client.TelegramClient;
import com.studenttask.telegram.client.TelegramException;
import com.studenttask.telegram.dto.TelegramUpdate;
import com.studenttask.telegram.service.TelegramUpdateHandler;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * Development-only long polling (one instance only). It starts only when {@code app.telegram.mode=polling},
 * runners are enabled and a bot token is configured. With a placeholder token Telegram answers 401/404 (PERMANENT):
 * that is logged as ONE sanitized WARN per failure streak and polling backs off for minutes instead of spamming.
 * Only the failure kind is logged (never the token, URL, chat id or update content).
 */
@Component
public class TelegramPollingRunner implements SmartLifecycle {

    static final int LONG_POLL_SECONDS = 30;
    static final Duration PERMANENT_BACKOFF = Duration.ofMinutes(5);
    static final Duration RETRY_MIN = Duration.ofSeconds(5);
    static final Duration RETRY_MAX = Duration.ofSeconds(60);
    private static final Logger log = LoggerFactory.getLogger(TelegramPollingRunner.class);

    private final AppProperties props;
    private final TelegramClient client;
    private final TelegramUpdateHandler handler;

    private volatile boolean running;
    private Thread thread;
    private long offset;
    private boolean webhookCleared;
    private boolean warned;
    private Duration retryDelay = RETRY_MIN;

    public TelegramPollingRunner(AppProperties props, TelegramClient client, TelegramUpdateHandler handler) {
        this.props = props;
        this.client = client;
        this.handler = handler;
    }

    /** One iteration; returns how long the caller must wait before the next one (zero after a successful poll). */
    public Duration pollOnce() {
        try {
            if (!webhookCleared) {
                client.deleteWebhook(); // polling and a webhook are mutually exclusive at Telegram
                webhookCleared = true;
            }
            List<TelegramUpdate> updates = client.getUpdates(offset, LONG_POLL_SECONDS);
            for (TelegramUpdate update : updates) {
                dispatch(update);
                if (update.updateId() != null) {
                    offset = Math.max(offset, update.updateId() + 1);
                }
            }
            warned = false;
            retryDelay = RETRY_MIN;
            return Duration.ZERO;
        } catch (TelegramException e) {
            return onFailure(e.kind(), e.retryAfter());
        } catch (RuntimeException e) {
            return onFailure(TelegramException.Kind.RETRYABLE, null);
        }
    }

    private void dispatch(TelegramUpdate update) {
        try {
            handler.handle(update);
        } catch (RuntimeException e) {
            // Never retry a poisoned update: the offset still moves past it.
            log.error("Telegram update handling failed ({})", e.getClass().getSimpleName());
        }
    }

    private Duration onFailure(TelegramException.Kind kind, Duration retryAfter) {
        Duration wait;
        if (kind == TelegramException.Kind.PERMANENT) {
            wait = PERMANENT_BACKOFF;
        } else {
            wait = retryDelay;
            if (retryAfter != null && retryAfter.compareTo(wait) > 0) {
                wait = retryAfter;
            }
            retryDelay = retryDelay.multipliedBy(2).compareTo(RETRY_MAX) > 0 ? RETRY_MAX : retryDelay.multipliedBy(2);
        }
        if (!warned) {
            warned = true;
            log.warn("Telegram polling failed ({}); check TELEGRAM_BOT_TOKEN. Retrying in {} s, further failures are "
                    + "logged at DEBUG until a poll succeeds", kind, wait.toSeconds());
        } else {
            log.debug("Telegram polling still failing ({})", kind);
        }
        return wait;
    }

    @Override
    public void start() {
        AppProperties.Telegram telegram = props.telegram();
        boolean enabled = telegram != null && telegram.mode() == AppProperties.Telegram.Mode.POLLING
                && telegram.runnersEnabled() && telegram.botToken() != null && !telegram.botToken().isBlank();
        if (!enabled || running) {
            return;
        }
        running = true;
        thread = new Thread(this::loop, "telegram-polling");
        thread.setDaemon(true);
        thread.start();
    }

    private void loop() {
        while (running) {
            Duration wait = pollOnce();
            if (!wait.isZero()) {
                try {
                    Thread.sleep(wait.toMillis());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    @Override
    public void stop() {
        running = false;
        if (thread != null) {
            thread.interrupt();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
