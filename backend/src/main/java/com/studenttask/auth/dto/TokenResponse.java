package com.studenttask.auth.dto;

/** Body of login/refresh. The refresh token travels only in the httpOnly cookie. */
public record TokenResponse(String accessToken, long expiresIn) {
}
