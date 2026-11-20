package com.studenttask.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.studenttask.config.AppProperties;
import com.studenttask.telegram.client.TelegramClient;
import com.studenttask.telegram.client.TelegramException;
import com.studenttask.telegram.client.TelegramException.Kind;
import com.studenttask.telegram.dto.TelegramUpdate;
import com.studenttask.telegram.runner.TelegramPollingRunner;
import com.studenttask.telegram.service.TelegramUpdateHandler;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/** Fake client only; nothing here can reach Telegram. */
class TelegramPollingRunnerTest {

    private static final String TOKEN = "PLACEHOLDER-synthetic-token";

    private final TelegramClient client = mock(TelegramClient.class);
    private final TelegramUpdateHandler handler = mock(TelegramUpdateHandler.class);
    private ListAppender<ILoggingEvent> logs;
    private Logger runnerLogger;

    @BeforeEach
    void captureLogs() {
        runnerLogger = (Logger) LoggerFactory.getLogger(TelegramPollingRunner.class);
        runnerLogger.setLevel(Level.DEBUG);
        logs = new ListAppender<>();
        logs.start();
        runnerLogger.addAppender(logs);
    }

    @AfterEach
    void release() {
        runnerLogger.detachAppender(logs);
    }

    private TelegramPollingRunner runner(String token) {
        AppProperties props = new AppProperties(null, new AppProperties.Telegram(token, "secret", "some_bot",
                AppProperties.Telegram.Mode.POLLING, "https://api.telegram.org", "", true), null, null, null);
        return new TelegramPollingRunner(props, client, handler);
    }

    private static TelegramUpdate update(long id) {
        return new TelegramUpdate(id, new TelegramUpdate.Message(new TelegramUpdate.Chat(9L, "private"), "hi"));
    }

    @Test
    void blank_token_does_not_start_and_never_calls_telegram() {
        for (String blank : new String[] {"", "  ", null}) {
            TelegramPollingRunner r = runner(blank);
            r.start();
            assertThat(r.isRunning()).isFalse();
            r.stop();
        }
        verifyNoInteractions(client, handler);
        assertThat(logs.list).noneMatch(e -> e.getLevel().isGreaterOrEqual(Level.WARN));
    }

    @Test
    void permanent_error_logs_one_sanitized_warn_and_backs_off_for_minutes() {
        TelegramPollingRunner r = runner(TOKEN);
        org.mockito.Mockito.doThrow(new TelegramException(Kind.PERMANENT, null, "Telegram menolak permintaan (401)"))
                .when(client).deleteWebhook();

        Duration first = r.pollOnce();
        Duration second = r.pollOnce();
        Duration third = r.pollOnce();

        assertThat(first).isGreaterThanOrEqualTo(Duration.ofMinutes(3));
        assertThat(second).isGreaterThanOrEqualTo(Duration.ofMinutes(3));
        assertThat(third).isGreaterThanOrEqualTo(Duration.ofMinutes(3));
        verify(client, never()).getUpdates(anyLong(), anyInt());
        assertThat(logs.list.stream().filter(e -> e.getLevel() == Level.WARN)).hasSize(1);
        assertThat(logs.list).noneMatch(e -> e.getFormattedMessage().contains(TOKEN) || e.getThrowableProxy() != null);
    }

    @Test
    void polling_permanent_error_after_webhook_cleared_also_warns_once() {
        TelegramPollingRunner r = runner(TOKEN);
        when(client.getUpdates(anyLong(), anyInt())).thenThrow(new TelegramException(Kind.PERMANENT, null, "nope"));

        r.pollOnce();
        r.pollOnce();

        verify(client, times(1)).deleteWebhook();
        assertThat(logs.list.stream().filter(e -> e.getLevel() == Level.WARN)).hasSize(1);
    }

    @Test
    void updates_are_handled_and_offset_advances_past_the_last_update() {
        TelegramPollingRunner r = runner(TOKEN);
        when(client.getUpdates(0L, 30)).thenReturn(List.of(update(10), update(11)));
        when(client.getUpdates(12L, 30)).thenReturn(List.of());

        r.pollOnce();
        r.pollOnce();

        verify(handler, times(2)).handle(any());
        verify(client).getUpdates(12L, 30);
        verify(client, times(1)).deleteWebhook();
    }

    @Test
    void a_failing_handler_does_not_stop_the_batch_or_repeat_the_update() {
        TelegramPollingRunner r = runner(TOKEN);
        when(client.getUpdates(0L, 30)).thenReturn(List.of(update(1), update(2)));
        org.mockito.Mockito.doThrow(new IllegalStateException("boom " + TOKEN)).when(handler).handle(update(1));

        r.pollOnce();

        verify(handler, times(2)).handle(any());
        verify(client, never()).getUpdates(1L, 30);
        assertThat(logs.list).noneMatch(e -> e.getFormattedMessage().contains(TOKEN));
    }

    @Test
    void retryable_error_waits_at_least_retry_after() {
        TelegramPollingRunner r = runner(TOKEN);
        when(client.getUpdates(anyLong(), anyInt()))
                .thenThrow(new TelegramException(Kind.RETRYABLE, Duration.ofSeconds(42), "limited"));

        assertThat(r.pollOnce()).isGreaterThanOrEqualTo(Duration.ofSeconds(42));
    }
}
