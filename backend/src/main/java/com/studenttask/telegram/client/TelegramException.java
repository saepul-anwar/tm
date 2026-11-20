package com.studenttask.telegram.client;

import java.time.Duration;

/**
 * Failure talking to Telegram. The message is always sanitized: it never contains the bot token, the request URL
 * or the response body. {@code retryAfter} is {@code null} unless Telegram asked for a wait (HTTP 429).
 */
public class TelegramException extends RuntimeException {

    public enum Kind { RETRYABLE, PERMANENT }

    private final Kind kind;
    private final Duration retryAfter;

    public TelegramException(Kind kind, Duration retryAfter, String sanitizedMessage) {
        super(sanitizedMessage);
        this.kind = kind;
        this.retryAfter = retryAfter;
    }

    public Kind kind() {
        return kind;
    }

    public Duration retryAfter() {
        return retryAfter;
    }
}
