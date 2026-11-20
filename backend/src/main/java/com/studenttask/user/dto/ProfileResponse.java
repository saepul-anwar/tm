package com.studenttask.user.dto;

public record ProfileResponse(String fullName, String email, String timezone, TelegramStatus telegram) {
}
