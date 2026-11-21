package com.studenttask.telegram.dto;

import java.time.Instant;

public record LinkResponse(String deepLink, Instant expiresAt) {
}
