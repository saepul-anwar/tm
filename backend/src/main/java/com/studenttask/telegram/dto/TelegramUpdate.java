package com.studenttask.telegram.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** The minimal slice of a Telegram update this app reads; everything else is ignored. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramUpdate(@JsonProperty("update_id") Long updateId, Message message) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(Chat chat, String text) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Chat(Long id, String type) {
    }
}
