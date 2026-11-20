package com.studenttask.auth.dto;

/** Service-level result; {@code refreshToken} is the raw value and must only be sent as a cookie. */
public record AuthTokens(String accessToken, long expiresInSeconds, String refreshToken) {

    @Override
    public String toString() {
        return "AuthTokens[redacted]";
    }
}
