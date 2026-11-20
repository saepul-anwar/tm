package com.studenttask.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studenttask.telegram.client.HttpTelegramClient;
import com.studenttask.telegram.client.TelegramException;
import com.studenttask.telegram.client.TelegramException.Kind;
import com.studenttask.telegram.dto.TelegramUpdate;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Talks to a local fake server only; the real Telegram API is never contacted. The token below is synthetic. */
class HttpTelegramClientTest {

    private static final String TOKEN = "123456:SYNTHETIC-token_for-tests";

    private HttpServer server;
    private final AtomicInteger status = new AtomicInteger(200);
    private volatile String responseBody = "{\"ok\":true,\"result\":{\"message_id\":77}}";
    private volatile long delayMillis;
    private final List<String> paths = new CopyOnWriteArrayList<>();
    private final List<String> bodies = new CopyOnWriteArrayList<>();
    private HttpTelegramClient client;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            paths.add(exchange.getRequestURI().getPath());
            bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            try {
                if (delayMillis > 0) {
                    Thread.sleep(delayMillis);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            byte[] out = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            try {
                exchange.sendResponseHeaders(status.get(), out.length);
                exchange.getResponseBody().write(out);
            } catch (IOException ignored) {
                // client gave up (timeout test)
            } finally {
                exchange.close();
            }
        });
        server.start();
        client = newClient(Duration.ofSeconds(2), Duration.ofSeconds(2));
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private HttpTelegramClient newClient(Duration connect, Duration request) {
        return new HttpTelegramClient("http://127.0.0.1:" + server.getAddress().getPort(), TOKEN,
                new ObjectMapper(), connect, request);
    }

    private TelegramException failure(Runnable call) {
        Throwable t = org.assertj.core.api.Assertions.catchThrowable(call::run);
        assertThat(t).isInstanceOf(TelegramException.class);
        return (TelegramException) t;
    }

    private static void assertSanitized(Throwable t) {
        for (Throwable x = t; x != null; x = x.getCause()) {
            assertThat(String.valueOf(x.getMessage())).doesNotContain(TOKEN).doesNotContain("SYNTHETIC")
                    .doesNotContain("/bot").doesNotContain("127.0.0.1");
            assertThat(x.toString()).doesNotContain(TOKEN);
        }
    }

    @Test
    void send_message_posts_plain_text_without_parse_mode_and_returns_message_id() {
        assertThat(client.sendMessage(4242L, "Halo <b>dunia</b> & _teman_").messageId()).isEqualTo(77L);

        assertThat(paths).containsExactly("/bot" + TOKEN + "/sendMessage");
        assertThat(bodies.get(0)).contains("\"chat_id\":4242").contains("Halo <b>dunia</b> & _teman_")
                .doesNotContain("parse_mode");
    }

    @Test
    void get_updates_parses_minimal_updates_and_ignores_unknown_fields() {
        responseBody = "{\"ok\":true,\"result\":[{\"update_id\":10,\"extra\":1,\"message\":{\"message_id\":1,"
                + "\"chat\":{\"id\":555,\"type\":\"private\",\"first_name\":\"X\"},\"text\":\"/start abc\"}}]}";

        List<TelegramUpdate> updates = client.getUpdates(10, 0);

        assertThat(updates).hasSize(1);
        assertThat(updates.get(0).updateId()).isEqualTo(10L);
        assertThat(updates.get(0).message().chat().type()).isEqualTo("private");
        assertThat(updates.get(0).message().text()).isEqualTo("/start abc");
        assertThat(paths).containsExactly("/bot" + TOKEN + "/getUpdates");
        assertThat(bodies.get(0)).contains("\"offset\":10");
    }

    @Test
    void set_webhook_sends_url_and_secret_and_delete_webhook_calls_endpoint() {
        responseBody = "{\"ok\":true,\"result\":true}";

        client.setWebhook("https://example.test/api/telegram/webhook", "synthetic-secret");
        client.deleteWebhook();

        assertThat(paths).containsExactly("/bot" + TOKEN + "/setWebhook", "/bot" + TOKEN + "/deleteWebhook");
        assertThat(bodies.get(0)).contains("https://example.test/api/telegram/webhook")
                .contains("\"secret_token\":\"synthetic-secret\"");
    }

    @Test
    void http_429_with_retry_after_is_retryable_with_that_duration() {
        status.set(429);
        responseBody = "{\"ok\":false,\"error_code\":429,\"description\":\"Too Many Requests: retry after 7\","
                + "\"parameters\":{\"retry_after\":7}}";

        TelegramException e = failure(() -> client.sendMessage(1L, "x"));

        assertThat(e.kind()).isEqualTo(Kind.RETRYABLE);
        assertThat(e.retryAfter()).isEqualTo(Duration.ofSeconds(7));
        assertSanitized(e);
    }

    @Test
    void http_5xx_is_retryable() {
        status.set(502);
        responseBody = "{\"ok\":false,\"description\":\"Bad Gateway\"}";

        TelegramException e = failure(() -> client.sendMessage(1L, "x"));

        assertThat(e.kind()).isEqualTo(Kind.RETRYABLE);
        assertSanitized(e);
    }

    @Test
    void http_403_blocked_by_user_is_permanent() {
        status.set(403);
        responseBody = "{\"ok\":false,\"error_code\":403,\"description\":\"Forbidden: bot was blocked by the user\"}";

        TelegramException e = failure(() -> client.sendMessage(1L, "x"));

        assertThat(e.kind()).isEqualTo(Kind.PERMANENT);
        assertSanitized(e);
    }

    @Test
    void http_400_401_404_are_permanent() {
        for (int code : new int[] {400, 401, 404}) {
            status.set(code);
            responseBody = "{\"ok\":false,\"error_code\":" + code + ",\"description\":\"nope\"}";
            TelegramException e = failure(() -> client.sendMessage(1L, "x"));
            assertThat(e.kind()).as("status %d", code).isEqualTo(Kind.PERMANENT);
            assertSanitized(e);
        }
    }

    @Test
    void read_timeout_is_retryable_and_sanitized() {
        delayMillis = 1500;
        HttpTelegramClient slow = newClient(Duration.ofSeconds(2), Duration.ofMillis(300));

        TelegramException e = failure(() -> slow.sendMessage(1L, "x"));

        assertThat(e.kind()).isEqualTo(Kind.RETRYABLE);
        assertSanitized(e);
    }

    @Test
    void connection_refused_is_retryable_and_sanitized() {
        HttpTelegramClient dead = new HttpTelegramClient("http://127.0.0.1:1", TOKEN, new ObjectMapper(),
                Duration.ofSeconds(1), Duration.ofSeconds(1));

        TelegramException e = failure(() -> dead.sendMessage(1L, "x"));

        assertThat(e.kind()).isEqualTo(Kind.RETRYABLE);
        assertSanitized(e);
    }

    @Test
    void ok_false_with_http_200_is_permanent_and_garbage_body_is_retryable() {
        responseBody = "{\"ok\":false,\"description\":\"weird\"}";
        assertThat(failure(() -> client.sendMessage(1L, "x")).kind()).isEqualTo(Kind.PERMANENT);

        responseBody = "<html>not json</html>";
        TelegramException garbage = failure(() -> client.sendMessage(1L, "x"));
        assertThat(garbage.kind()).isEqualTo(Kind.RETRYABLE);
        assertSanitized(garbage);
    }

    @Test
    void error_description_from_telegram_is_not_copied_into_the_message() {
        status.set(400);
        responseBody = "{\"ok\":false,\"error_code\":400,\"description\":\"echo " + TOKEN + "\"}";

        assertThatThrownBy(() -> client.sendMessage(1L, "x")).isInstanceOf(TelegramException.class)
                .satisfies(t -> assertSanitized(t));
    }
}
