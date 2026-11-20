package com.studenttask.telegram.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studenttask.telegram.client.TelegramException.Kind;
import com.studenttask.telegram.dto.SendResult;
import com.studenttask.telegram.dto.TelegramUpdate;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Telegram Bot API over the JDK HttpClient with explicit connect and per-request timeouts and no redirects.
 *
 * <p>The bot token is part of the request path, so every failure is converted to a {@link TelegramException}
 * with a fixed, sanitized message: no URL, no token, no response body and no cause (JDK exception messages and
 * causes can embed the URI). Mapping: 429 and 5xx, timeouts, IO errors and unreadable responses are RETRYABLE
 * (429 carries {@code parameters.retry_after}); other 4xx and {@code ok:false} answers are PERMANENT.
 * Messages are sent as plain text: there is deliberately no parse_mode.
 */
public class HttpTelegramClient implements TelegramClient {

    private static final String UNREADABLE = "Respons Telegram tidak dapat dibaca";

    private final String apiBase;
    private final String botToken;
    private final ObjectMapper mapper;
    private final Duration requestTimeout;
    private final HttpClient http;

    public HttpTelegramClient(String apiBase, String botToken, ObjectMapper mapper, Duration connectTimeout,
            Duration requestTimeout) {
        this.apiBase = apiBase.endsWith("/") ? apiBase.substring(0, apiBase.length() - 1) : apiBase;
        this.botToken = botToken;
        this.mapper = mapper;
        this.requestTimeout = requestTimeout;
        this.http = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    @Override
    public SendResult sendMessage(long chatId, String text) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("chat_id", chatId);
        payload.put("text", text);
        JsonNode result = call("sendMessage", payload, Duration.ZERO);
        return new SendResult(result.path("message_id").asLong());
    }

    @Override
    public List<TelegramUpdate> getUpdates(long offset, int timeoutSeconds) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("offset", offset);
        payload.put("timeout", timeoutSeconds);
        payload.put("allowed_updates", List.of("message"));
        JsonNode result = call("getUpdates", payload, Duration.ofSeconds(timeoutSeconds));
        List<TelegramUpdate> updates = new ArrayList<>();
        try {
            for (JsonNode node : result) {
                updates.add(mapper.treeToValue(node, TelegramUpdate.class));
            }
        } catch (IOException | IllegalArgumentException e) {
            throw new TelegramException(Kind.RETRYABLE, null, UNREADABLE);
        }
        return updates;
    }

    @Override
    public void setWebhook(String url, String secret) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("url", url);
        payload.put("secret_token", secret);
        payload.put("allowed_updates", List.of("message"));
        call("setWebhook", payload, Duration.ZERO);
    }

    @Override
    public void deleteWebhook() {
        call("deleteWebhook", Map.of(), Duration.ZERO);
    }

    private JsonNode call(String method, Map<String, Object> payload, Duration extraTimeout) {
        HttpResponse<byte[]> response;
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(apiBase + "/bot" + botToken + "/" + method))
                    .timeout(requestTimeout.plus(extraTimeout))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(payload)))
                    .build();
            response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (HttpTimeoutException e) {
            throw new TelegramException(Kind.RETRYABLE, null, "Telegram tidak merespons (timeout)");
        } catch (IOException e) {
            throw new TelegramException(Kind.RETRYABLE, null, "Telegram tidak dapat dihubungi");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TelegramException(Kind.RETRYABLE, null, "Permintaan ke Telegram dibatalkan");
        } catch (IllegalArgumentException e) {
            throw new TelegramException(Kind.PERMANENT, null, "Konfigurasi Telegram tidak valid");
        }
        return interpret(response);
    }

    private JsonNode interpret(HttpResponse<byte[]> response) {
        int status = response.statusCode();
        JsonNode body = parse(response.body());
        if (status == 429) {
            long seconds = body == null ? 0 : body.path("parameters").path("retry_after").asLong(0);
            Duration retryAfter = seconds > 0 ? Duration.ofSeconds(seconds) : null;
            throw new TelegramException(Kind.RETRYABLE, retryAfter, "Telegram membatasi laju permintaan (HTTP 429)");
        }
        if (status >= 500) {
            throw new TelegramException(Kind.RETRYABLE, null, "Telegram sedang bermasalah (HTTP " + status + ")");
        }
        if (status >= 400) {
            throw new TelegramException(Kind.PERMANENT, null, "Telegram menolak permintaan (HTTP " + status + ")");
        }
        if (body == null) {
            throw new TelegramException(Kind.RETRYABLE, null, UNREADABLE);
        }
        if (!body.path("ok").asBoolean(false)) {
            throw new TelegramException(Kind.PERMANENT, null, "Telegram menolak permintaan");
        }
        return body.path("result");
    }

    private JsonNode parse(byte[] bytes) {
        try {
            JsonNode node = mapper.readTree(bytes);
            return node != null && node.isObject() ? node : null;
        } catch (IOException e) {
            return null;
        }
    }
}
