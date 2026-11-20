package com.studenttask.telegram.dto;

/** Outcome of a successful sendMessage: Telegram's id of the created message. */
public record SendResult(long messageId) {
}
