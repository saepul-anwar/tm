package com.studenttask.auth.controller;

import com.studenttask.auth.dto.AuthTokens;
import com.studenttask.auth.dto.LoginRequest;
import com.studenttask.auth.dto.RegisterRequest;
import com.studenttask.auth.dto.TokenResponse;
import com.studenttask.auth.dto.UserProfileResponse;
import com.studenttask.auth.service.AuthService;
import com.studenttask.auth.service.RefreshTokenService;
import com.studenttask.common.api.ApiResponse;
import com.studenttask.config.AppProperties;
import com.studenttask.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    static final String REFRESH_COOKIE = "refresh_token";
    private static final String COOKIE_PATH = "/api/auth";

    private final AuthService authService;
    private final boolean secureCookie;

    public AuthController(AuthService authService, AppProperties properties) {
        this.authService = authService;
        this.secureCookie = properties.auth().refreshCookieSecure();
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserProfileResponse>> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(authService.register(request)));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponse>> login(@Valid @RequestBody LoginRequest request) {
        return tokenResponse(authService.login(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
        return tokenResponse(authService.refresh(refreshToken));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
        authService.logout(refreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookie("", 0).toString())
                .build();
    }

    @GetMapping("/me")
    public ApiResponse<UserProfileResponse> me() {
        return ApiResponse.ok(authService.me(SecurityUtils.currentUserId()));
    }

    private ResponseEntity<ApiResponse<TokenResponse>> tokenResponse(AuthTokens tokens) {
        long maxAge = RefreshTokenService.REFRESH_TOKEN_TTL.toSeconds();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(tokens.refreshToken(), maxAge).toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(ApiResponse.ok(new TokenResponse(tokens.accessToken(), tokens.expiresInSeconds())));
    }

    private ResponseCookie refreshCookie(String value, long maxAgeSeconds) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Strict")
                .path(COOKIE_PATH)
                .maxAge(maxAgeSeconds)
                .build();
    }
}
