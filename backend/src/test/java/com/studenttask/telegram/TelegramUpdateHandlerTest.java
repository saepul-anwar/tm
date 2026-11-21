package com.studenttask.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.studenttask.security.UserRateLimiter;
import com.studenttask.telegram.client.TelegramClient;
import com.studenttask.telegram.dto.LinkResult;
import com.studenttask.telegram.dto.TelegramUpdate;
import com.studenttask.telegram.service.TelegramLinkService;
import com.studenttask.telegram.service.TelegramUpdateHandler;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Synthetic chat ids and addresses only. */
class TelegramUpdateHandlerTest {

    private static final long CHAT = 4242L;
    private static final String EMAIL = "alice@example.test";

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-11-20T00:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final TelegramLinkService links = mock(TelegramLinkService.class);
    private final TelegramClient client = mock(TelegramClient.class);
    private final MutableClock clock = new MutableClock();
    private final TelegramUpdateHandler handler =
            new TelegramUpdateHandler(links, client, new UserRateLimiter(clock));

    private static TelegramUpdate update(long chatId, String type, String text) {
        return new TelegramUpdate(1L, new TelegramUpdate.Message(new TelegramUpdate.Chat(chatId, type), text));
    }

    private String reply(long chatId) {
        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(client).sendMessage(eq(chatId), text.capture());
        return text.getValue();
    }

    // ---- F8: masked email in the LINKED reply ----

    @Test
    void linked_reply_names_the_account_with_a_masked_email_and_explains_stop() {
        when(links.handleStart(CHAT, "private", "tok")).thenReturn(LinkResult.LINKED);
        when(links.findLinkedEmail(CHAT)).thenReturn(Optional.of(EMAIL));

        handler.handle(update(CHAT, "private", "/start tok"));

        assertThat(reply(CHAT)).contains("a***@example.test").contains("Bukan kamu? Kirim /stop")
                .containsIgnoringCase("terhubung").doesNotContain(EMAIL).doesNotContain("alice").doesNotContain("tok");
    }

    @Test
    void linked_reply_still_explains_stop_when_the_account_lookup_finds_nothing() {
        when(links.handleStart(CHAT, "private", "tok")).thenReturn(LinkResult.LINKED);
        when(links.findLinkedEmail(CHAT)).thenReturn(Optional.empty());

        handler.handle(update(CHAT, "private", "/start tok"));

        assertThat(reply(CHAT)).containsIgnoringCase("terhubung").contains("/stop");
    }

    // ---- F8: /stop ----

    @Test
    void stop_in_a_private_chat_unlinks_and_confirms() {
        when(links.disconnectByChat(CHAT)).thenReturn(true);

        handler.handle(update(CHAT, "private", "/stop"));

        verify(links).disconnectByChat(CHAT);
        assertThat(reply(CHAT)).containsIgnoringCase("diputus");
    }

    @Test
    void stop_accepts_a_bot_mention_and_any_case() {
        when(links.disconnectByChat(CHAT)).thenReturn(true);

        handler.handle(update(CHAT, "private", "/STOP@some_bot"));

        verify(links).disconnectByChat(CHAT);
    }

    @Test
    void stop_when_nothing_is_linked_says_so_and_reveals_nothing() {
        when(links.disconnectByChat(CHAT)).thenReturn(false);

        handler.handle(update(CHAT, "private", "/stop"));

        assertThat(reply(CHAT)).containsIgnoringCase("tidak ada akun").doesNotContain("@");
    }

    @Test
    void stop_in_a_group_is_ignored_without_reply_or_lookup() {
        for (String type : new String[] {"group", "supergroup", "channel", null}) {
            handler.handle(update(-100123L, type, "/stop"));
        }

        verifyNoInteractions(links, client);
    }

    // ---- F1: per-chat limiter ----

    @Test
    void sixth_command_per_minute_from_one_chat_is_dropped_before_any_lookup_or_reply() {
        when(links.disconnectByChat(CHAT)).thenReturn(false);
        for (int i = 0; i < 5; i++) {
            handler.handle(update(CHAT, "private", "/stop"));
        }
        verify(links, times(5)).disconnectByChat(CHAT);
        verify(client, times(5)).sendMessage(eq(CHAT), anyString());

        handler.handle(update(CHAT, "private", "/stop"));
        handler.handle(update(CHAT, "private", "/start some-token"));
        handler.handle(update(CHAT, "private", "/start"));

        verifyNoMoreInteractions(links, client);
    }

    @Test
    void limiter_is_per_chat_and_recovers_after_the_window() {
        for (int i = 0; i < 6; i++) {
            handler.handle(update(CHAT, "private", "/stop"));
        }

        handler.handle(update(CHAT + 1, "private", "/stop"));
        verify(links).disconnectByChat(CHAT + 1);

        clock.advance(Duration.ofMinutes(1));
        handler.handle(update(CHAT, "private", "/stop"));
        verify(links, times(6)).disconnectByChat(CHAT);
    }

    @Test
    void messages_that_are_not_commands_do_not_use_the_budget() {
        for (int i = 0; i < 20; i++) {
            handler.handle(update(CHAT, "private", "hello"));
        }
        when(links.disconnectByChat(CHAT)).thenReturn(true);

        handler.handle(update(CHAT, "private", "/stop"));

        verify(links).disconnectByChat(CHAT);
        verify(client, never()).sendMessage(anyLong(), eq("hello"));
    }
}
