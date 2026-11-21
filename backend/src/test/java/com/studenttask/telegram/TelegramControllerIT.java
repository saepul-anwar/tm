package com.studenttask.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studenttask.MySqlTestBase;
import com.studenttask.telegram.client.TelegramClient;
import com.studenttask.telegram.client.TelegramException;
import com.studenttask.telegram.client.TelegramException.Kind;
import com.studenttask.telegram.dto.SendResult;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** The Telegram client is mocked: the real Telegram API is never called. All data is synthetic. */
@AutoConfigureMockMvc
class TelegramControllerIT extends MySqlTestBase {

    private static final String PASSWORD = "Passw0rd-Synthetic1";
    private static final String BOT_TOKEN = "test-only-synthetic-bot-token";
    private static final String WEBHOOK_SECRET = "test-only-synthetic-webhook-secret";
    private static final String SECRET_HEADER = "X-Telegram-Bot-Api-Secret-Token";
    private static final AtomicInteger IP_COUNTER = new AtomicInteger(5000);

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    @MockitoBean
    TelegramClient client;

    private ListAppender<ILoggingEvent> logs;
    private Logger rootLogger;
    private Level previousLevel;

    private record Account(long id, String accessToken) {
    }

    @BeforeEach
    void captureLogs() {
        rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        previousLevel = rootLogger.getLevel();
        rootLogger.setLevel(Level.DEBUG);
        logs = new ListAppender<>();
        logs.start();
        rootLogger.addAppender(logs);
        reset(client);
    }

    @AfterEach
    void releaseLogs() {
        rootLogger.detachAppender(logs);
        rootLogger.setLevel(previousLevel);
    }

    private static MockHttpServletRequestBuilder freshIp(MockHttpServletRequestBuilder builder) {
        int n = IP_COUNTER.incrementAndGet();
        return builder.with(request -> {
            request.setRemoteAddr("10." + (n / 250) + "." + (n % 250 + 1) + ".9");
            return request;
        });
    }

    private Account newAccount() throws Exception {
        String email = "tgc-" + UUID.randomUUID() + "@example.test";
        mvc.perform(freshIp(post("/api/auth/register")).contentType("application/json")
                .content(json.writeValueAsString(Map.of("fullName", "Dewi Synthetic", "email", email,
                        "password", PASSWORD, "confirmPassword", PASSWORD)))).andReturn();
        MvcResult login = mvc.perform(freshIp(post("/api/auth/login")).contentType("application/json")
                .content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD)))).andReturn();
        String token = body(login).at("/data/accessToken").asText();
        long id = jdbc.queryForObject("select id from users where email = ?", Long.class, email);
        return new Account(id, token);
    }

    private JsonNode body(MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsString());
    }

    private MvcResult authed(Account a, MockHttpServletRequestBuilder b) throws Exception {
        return mvc.perform(b.header("Authorization", "Bearer " + a.accessToken())).andReturn();
    }

    private static int status(MvcResult r) {
        return r.getResponse().getStatus();
    }

    private String connect(Account a) throws Exception {
        MvcResult r = authed(a, post("/api/telegram/connect"));
        assertThat(status(r)).isEqualTo(200);
        String link = body(r).at("/data/deepLink").asText();
        return link.substring(link.indexOf("?start=") + 7);
    }

    private static long newChatId() {
        return ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_000_000_000_000L);
    }

    private static String update(long chatId, String chatType, String text) {
        return "{\"update_id\":1,\"unknown_field\":{\"a\":1},\"message\":{\"message_id\":5,\"chat\":{\"id\":" + chatId
                + ",\"type\":\"" + chatType + "\",\"first_name\":\"Synthetic\"},\"text\":" + jsonString(text) + "}}";
    }

    private static String jsonString(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private MvcResult webhook(String secret, String payload) throws Exception {
        MockHttpServletRequestBuilder b = post("/api/telegram/webhook").contentType("application/json").content(payload);
        if (secret != null) {
            b.header(SECRET_HEADER, secret);
        }
        return mvc.perform(b).andReturn();
    }

    private Long chatOf(long userId) {
        return jdbc.queryForObject("select telegram_chat_id from users where id = ?", Long.class, userId);
    }

    private String lastReply(long chatId) {
        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(client).sendMessage(eq(chatId), text.capture());
        return text.getValue();
    }

    // ---- webhook ----

    @Test
    void webhook_without_or_with_wrong_secret_returns_401() throws Exception {
        Account a = newAccount();
        String token = connect(a);
        long chat = newChatId();
        String payload = update(chat, "private", "/start " + token);

        for (String secret : new String[] {null, "", "wrong-secret", WEBHOOK_SECRET + "x", WEBHOOK_SECRET.substring(1)}) {
            MvcResult r = webhook(secret, payload);
            assertThat(status(r)).as("secret=%s", secret).isEqualTo(401);
            assertThat(body(r).at("/error/code").asText()).isEqualTo("UNAUTHENTICATED");
        }

        assertThat(chatOf(a.id())).isNull();
        verifyNoInteractions(client);
    }

    @Test
    void webhook_with_valid_secret_links_chat_and_replies_without_echoing_the_token() throws Exception {
        Account a = newAccount();
        String token = connect(a);
        long chat = newChatId();

        MvcResult r = webhook(WEBHOOK_SECRET, update(chat, "private", "/start " + token));

        assertThat(status(r)).isEqualTo(200);
        assertThat(r.getResponse().getContentAsString()).isEmpty();
        assertThat(chatOf(a.id())).isEqualTo(chat);
        assertThat(lastReply(chat)).containsIgnoringCase("terhubung").doesNotContain(token);
        MvcResult profile = authed(a, get("/api/profile"));
        assertThat(body(profile).at("/data/telegram/connected").asBoolean()).isTrue();
    }

    @Test
    void webhook_returns_200_empty_for_ignored_updates_and_malformed_json() throws Exception {
        String[] payloads = {
            "{\"update_id\":2,\"edited_message\":{\"chat\":{\"id\":1,\"type\":\"private\"},\"text\":\"/start abc\"}}",
            "{\"update_id\":3,\"message\":{\"chat\":{\"id\":1,\"type\":\"private\"},\"sticker\":{}}}",
            "{\"update_id\":4,\"message\":{\"text\":\"hello\"}}",
            "{\"update_id\":5,\"callback_query\":{\"id\":\"x\"}}",
            "{}", "[]", "null", "{{{ not json", "",
        };
        for (String p : payloads) {
            MvcResult r = webhook(WEBHOOK_SECRET, p);
            assertThat(status(r)).as(p).isEqualTo(200);
            assertThat(r.getResponse().getContentAsString()).as(p).isEmpty();
        }
        verifyNoInteractions(client);
        MvcResult hello = webhook(WEBHOOK_SECRET, update(newChatId(), "private", "hello there"));
        assertThat(status(hello)).isEqualTo(200);
        verifyNoInteractions(client);
    }

    @Test
    void group_chat_start_is_ignored() throws Exception {
        Account a = newAccount();
        String token = connect(a);

        MvcResult r = webhook(WEBHOOK_SECRET, update(-100987654321L, "group", "/start " + token));

        assertThat(status(r)).isEqualTo(200);
        assertThat(chatOf(a.id())).isNull();
        verifyNoInteractions(client);
        // /start without a token in a group is ignored too.
        webhook(WEBHOOK_SECRET, update(-100987654321L, "supergroup", "/start"));
        verifyNoInteractions(client);
    }

    @Test
    void start_without_token_replies_instructions() throws Exception {
        long chat = newChatId();

        MvcResult r = webhook(WEBHOOK_SECRET, update(chat, "private", "/start"));

        assertThat(status(r)).isEqualTo(200);
        assertThat(lastReply(chat)).containsIgnoringCase("aplikasi").containsIgnoringCase("hubungkan");
    }

    @Test
    void invalid_token_replies_to_recreate_it_from_the_app() throws Exception {
        long chat = newChatId();

        webhook(WEBHOOK_SECRET, update(chat, "private", "/start not-a-real-token"));

        assertThat(lastReply(chat)).containsIgnoringCase("tidak valid").containsIgnoringCase("buat ulang")
                .doesNotContain("not-a-real-token");
    }

    @Test
    void chat_taken_replies_that_chat_is_linked_to_another_account() throws Exception {
        Account owner = newAccount();
        Account other = newAccount();
        long chat = newChatId();
        webhook(WEBHOOK_SECRET, update(chat, "private", "/start " + connect(owner)));
        reset(client);

        webhook(WEBHOOK_SECRET, update(chat, "private", "/start " + connect(other)));

        assertThat(lastReply(chat)).containsIgnoringCase("akun lain");
        assertThat(chatOf(owner.id())).isEqualTo(chat);
        assertThat(chatOf(other.id())).isNull();
    }

    @Test
    void reply_failure_does_not_break_processing() throws Exception {
        Account a = newAccount();
        long chat = newChatId();
        when(client.sendMessage(anyLong(), anyString())).thenThrow(new TelegramException(Kind.RETRYABLE, null, "down"));

        MvcResult r = webhook(WEBHOOK_SECRET, update(chat, "private", "/start " + connect(a)));

        assertThat(status(r)).isEqualTo(200);
        assertThat(chatOf(a.id())).isEqualTo(chat);
    }

    // ---- F8: masked email and /stop ----

    private String emailOf(long userId) {
        return jdbc.queryForObject("select email from users where id = ?", String.class, userId);
    }

    @Test
    void linked_reply_shows_a_masked_email_and_the_stop_hint_but_never_the_full_address() throws Exception {
        Account a = newAccount();
        long chat = newChatId();
        String email = emailOf(a.id());

        webhook(WEBHOOK_SECRET, update(chat, "private", "/start " + connect(a)));

        String reply = lastReply(chat);
        assertThat(reply).contains("t***@example.test").contains("Bukan kamu? Kirim /stop")
                .doesNotContain(email).doesNotContain("tgc-");
    }

    @Test
    void stop_unlinks_the_chat_and_confirms_and_a_second_stop_says_nothing_is_linked() throws Exception {
        Account a = newAccount();
        long chat = newChatId();
        webhook(WEBHOOK_SECRET, update(chat, "private", "/start " + connect(a)));
        String pending = connect(a);
        reset(client);

        MvcResult first = webhook(WEBHOOK_SECRET, update(chat, "private", "/stop"));

        assertThat(status(first)).isEqualTo(200);
        assertThat(chatOf(a.id())).isNull();
        assertThat(jdbc.queryForObject("select telegram_connected_at is null from users where id = ?", Boolean.class, a.id()))
                .isTrue();
        assertThat(lastReply(chat)).containsIgnoringCase("diputus").doesNotContain(emailOf(a.id()));
        // The unused link token was dropped as well, so it cannot re-link the chat.
        reset(client);
        webhook(WEBHOOK_SECRET, update(chat, "private", "/start " + pending));
        assertThat(chatOf(a.id())).isNull();
        assertThat(lastReply(chat)).containsIgnoringCase("tidak valid");
        reset(client);

        webhook(WEBHOOK_SECRET, update(chat, "private", "/stop"));

        assertThat(lastReply(chat)).containsIgnoringCase("tidak ada akun");
    }

    @Test
    void stop_in_a_group_is_ignored_and_does_not_unlink_anything() throws Exception {
        Account a = newAccount();
        long groupChat = -100555444333L;
        webhook(WEBHOOK_SECRET, update(newChatId(), "private", "/start " + connect(a)));
        Long linked = chatOf(a.id());
        reset(client);

        MvcResult r = webhook(WEBHOOK_SECRET, update(groupChat, "group", "/stop"));

        assertThat(status(r)).isEqualTo(200);
        assertThat(chatOf(a.id())).isEqualTo(linked);
        verifyNoInteractions(client);
    }

    // ---- F1: per-chat limit through the full webhook path ----

    @Test
    void sixth_update_per_minute_from_one_chat_is_dropped_silently_and_another_chat_is_unaffected() throws Exception {
        long chat = newChatId();
        for (int i = 0; i < 5; i++) {
            assertThat(status(webhook(WEBHOOK_SECRET, update(chat, "private", "/start")))).isEqualTo(200);
        }
        verify(client, org.mockito.Mockito.times(5)).sendMessage(eq(chat), anyString());
        reset(client);

        MvcResult sixth = webhook(WEBHOOK_SECRET, update(chat, "private", "/start"));

        assertThat(status(sixth)).isEqualTo(200);
        assertThat(sixth.getResponse().getContentAsString()).isEmpty();
        verifyNoInteractions(client);
        long another = newChatId();
        webhook(WEBHOOK_SECRET, update(another, "private", "/start"));
        verify(client).sendMessage(eq(another), anyString());
    }

    // ---- connect / disconnect / test ----

    @Test
    void authenticated_routes_require_authentication() throws Exception {
        assertThat(status(mvc.perform(post("/api/telegram/connect")).andReturn())).isEqualTo(401);
        assertThat(status(mvc.perform(delete("/api/telegram/disconnect")).andReturn())).isEqualTo(401);
        assertThat(status(mvc.perform(post("/api/telegram/test")).andReturn())).isEqualTo(401);
    }

    @Test
    void connect_returns_deep_link_and_expiry() throws Exception {
        Account a = newAccount();

        MvcResult r = authed(a, post("/api/telegram/connect"));

        assertThat(status(r)).isEqualTo(200);
        assertThat(body(r).at("/data/deepLink").asText()).startsWith("https://t.me/test_only_bot?start=");
        assertThat(body(r).at("/data/expiresAt").asText()).isNotBlank();
    }

    @Test
    void connect_6th_per_minute_returns_429() throws Exception {
        Account a = newAccount();
        for (int i = 0; i < 5; i++) {
            assertThat(status(authed(a, post("/api/telegram/connect")))).isEqualTo(200);
        }

        MvcResult sixth = authed(a, post("/api/telegram/connect"));

        assertThat(status(sixth)).isEqualTo(429);
        assertThat(body(sixth).at("/error/code").asText()).isEqualTo("RATE_LIMITED");
        // The limit is per user, not per IP: another user is unaffected.
        assertThat(status(authed(newAccount(), post("/api/telegram/connect")))).isEqualTo(200);
    }

    @Test
    void disconnect_clears_chat_id() throws Exception {
        Account a = newAccount();
        webhook(WEBHOOK_SECRET, update(newChatId(), "private", "/start " + connect(a)));
        assertThat(chatOf(a.id())).isNotNull();

        assertThat(status(authed(a, delete("/api/telegram/disconnect")))).isEqualTo(204);
        assertThat(status(authed(a, delete("/api/telegram/disconnect")))).isEqualTo(204);

        assertThat(chatOf(a.id())).isNull();
        assertThat(jdbc.queryForObject("select telegram_connected_at is null from users where id = ?", Boolean.class, a.id()))
                .isTrue();
        MvcResult profile = authed(a, get("/api/profile"));
        assertThat(body(profile).at("/data/telegram/connected").asBoolean()).isFalse();
    }

    @Test
    void test_when_not_connected_returns_409() throws Exception {
        Account a = newAccount();

        MvcResult r = authed(a, post("/api/telegram/test"));

        assertThat(status(r)).isEqualTo(409);
        assertThat(body(r).at("/error/code").asText()).isEqualTo("CONFLICT");
        verifyNoInteractions(client);
    }

    @Test
    void test_when_connected_sends_a_plain_text_marked_test_message() throws Exception {
        Account a = newAccount();
        long chat = newChatId();
        webhook(WEBHOOK_SECRET, update(chat, "private", "/start " + connect(a)));
        reset(client);
        when(client.sendMessage(anyLong(), anyString())).thenReturn(new SendResult(1));

        MvcResult r = authed(a, post("/api/telegram/test"));

        assertThat(status(r)).isEqualTo(200);
        assertThat(lastReply(chat)).containsIgnoringCase("tes");
    }

    @Test
    void test_when_telegram_fails_returns_502_without_token_in_message() throws Exception {
        Account a = newAccount();
        webhook(WEBHOOK_SECRET, update(newChatId(), "private", "/start " + connect(a)));
        when(client.sendMessage(anyLong(), anyString())).thenThrow(
                new TelegramException(Kind.PERMANENT, null, "leaky https://api.telegram.org/bot" + BOT_TOKEN + "/sendMessage"));

        MvcResult r = authed(a, post("/api/telegram/test"));

        assertThat(status(r)).isEqualTo(502);
        assertThat(body(r).at("/error/code").asText()).isEqualTo("TELEGRAM_UNAVAILABLE");
        String response = r.getResponse().getContentAsString();
        assertThat(response).doesNotContain(BOT_TOKEN).doesNotContain("api.telegram.org").doesNotContain("/bot");
    }

    @Test
    void test_4th_per_minute_returns_429() throws Exception {
        Account a = newAccount();
        webhook(WEBHOOK_SECRET, update(newChatId(), "private", "/start " + connect(a)));
        when(client.sendMessage(anyLong(), anyString())).thenReturn(new SendResult(1));
        for (int i = 0; i < 3; i++) {
            assertThat(status(authed(a, post("/api/telegram/test")))).isEqualTo(200);
        }

        MvcResult fourth = authed(a, post("/api/telegram/test"));

        assertThat(status(fourth)).isEqualTo(429);
        assertThat(body(fourth).at("/error/code").asText()).isEqualTo("RATE_LIMITED");
    }

    @Test
    void bot_token_never_in_any_response_or_log() throws Exception {
        Account a = newAccount();
        long chat = newChatId();
        StringBuilder responses = new StringBuilder();
        String linkToken = connect(a);
        when(client.sendMessage(anyLong(), anyString())).thenThrow(
                new TelegramException(Kind.RETRYABLE, null, "leaky " + BOT_TOKEN + " " + linkToken));

        responses.append(webhook(WEBHOOK_SECRET, update(chat, "private", "/start " + linkToken)).getResponse().getContentAsString());
        responses.append(webhook("wrong", update(chat, "private", "/start " + linkToken)).getResponse().getContentAsString());
        responses.append(webhook(WEBHOOK_SECRET, "{{{ broken " + BOT_TOKEN).getResponse().getContentAsString());
        responses.append(authed(a, post("/api/telegram/test")).getResponse().getContentAsString());
        responses.append(authed(a, post("/api/telegram/connect")).getResponse().getContentAsString());
        responses.append(authed(a, get("/api/profile")).getResponse().getContentAsString());

        assertThat(responses.toString()).doesNotContain(BOT_TOKEN);
        for (ILoggingEvent event : logs.list) {
            StringBuilder line = new StringBuilder(event.getFormattedMessage());
            for (IThrowableProxy t = event.getThrowableProxy(); t != null; t = t.getCause()) {
                line.append(' ').append(t.getMessage());
            }
            assertThat(line.toString()).as("log line of %s", event.getLoggerName())
                    .doesNotContain(BOT_TOKEN).doesNotContain(linkToken).doesNotContain(WEBHOOK_SECRET);
        }
    }
}
