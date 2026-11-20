package com.studenttask.user.dto;

import java.time.Instant;

/** Only whether Telegram is linked and since when; the chat id itself is never exposed. */
public record TelegramStatus(boolean connected, Instant connectedAt) {
}
