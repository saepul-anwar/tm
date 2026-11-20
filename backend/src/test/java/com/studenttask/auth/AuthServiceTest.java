package com.studenttask.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.studenttask.auth.dto.AuthTokens;
import com.studenttask.auth.dto.LoginRequest;
import com.studenttask.auth.dto.RegisterRequest;
import com.studenttask.auth.dto.UserProfileResponse;
import com.studenttask.auth.service.AuthService;
import com.studenttask.auth.service.JwtService;
import com.studenttask.auth.service.RefreshTokenService;
import com.studenttask.common.error.AppException;
import com.studenttask.common.error.ErrorCode;
import com.studenttask.config.AppProperties;
import com.studenttask.user.entity.ReminderSettings;
import com.studenttask.user.entity.User;
import com.studenttask.user.mapper.UserMapper;
import com.studenttask.user.repository.ReminderSettingsRepository;
import com.studenttask.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class AuthServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-20T00:00:00Z"), ZoneOffset.UTC);

    private final UserRepository users = mock(UserRepository.class);
    private final ReminderSettingsRepository settings = mock(ReminderSettingsRepository.class);
    private final RefreshTokenService refreshTokens = mock(RefreshTokenService.class);
    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private AuthService service;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties(
                new AppProperties.Jwt("synthetic-secret-for-unit-tests-0123456789"), null, null, null,
                new AppProperties.Auth(true));
        JwtService jwt = new JwtService(props, CLOCK);
        service = new AuthService(users, settings, encoder, jwt, refreshTokens, new UserMapper(), CLOCK);
    }

    private static RegisterRequest register(String email) {
        return new RegisterRequest("Budi Santoso", email, "Passw0rd123", "Passw0rd123");
    }

    private void stubSave() {
        when(users.saveAndFlush(any(User.class))).thenAnswer(inv -> {
            User saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 42L);
            return saved;
        });
    }

    @Test
    void register_creates_user_and_default_reminder_settings_all_true() {
        stubSave();
        UserProfileResponse profile = service.register(register("Budi@Example.TEST"));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(users).saveAndFlush(userCaptor.capture());
        User saved = userCaptor.getValue();
        assertThat(saved.getEmail()).isEqualTo("budi@example.test");
        assertThat(saved.getPasswordHash()).isNotEqualTo("Passw0rd123");
        assertThat(encoder.matches("Passw0rd123", saved.getPasswordHash())).isTrue();
        assertThat(saved.getTimezone()).isEqualTo("Asia/Jakarta");

        ArgumentCaptor<ReminderSettings> settingsCaptor = ArgumentCaptor.forClass(ReminderSettings.class);
        verify(settings).save(settingsCaptor.capture());
        ReminderSettings s = settingsCaptor.getValue();
        assertThat(s.getUserId()).isEqualTo(42L);
        assertThat(List.of(s.isReminder24Hours(), s.isReminder6Hours(), s.isReminder1Hour(), s.isReminderAtDeadline()))
                .containsOnly(true);
        assertThat(profile.email()).isEqualTo("budi@example.test");
    }

    @Test
    void register_lowercases_email_and_rejects_duplicate_with_409() {
        when(users.existsByEmail("budi@example.test")).thenReturn(true);

        assertThatThrownBy(() -> service.register(register("  BUDI@example.test ")))
                .isInstanceOfSatisfying(AppException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.CONFLICT));
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void register_maps_concurrent_unique_violation_to_409() {
        when(users.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("dup"));

        assertThatThrownBy(() -> service.register(register("race@example.test")))
                .isInstanceOfSatisfying(AppException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.CONFLICT));
        verify(settings, never()).save(any());
    }

    private User existingUser() {
        User user = new User("Budi", "budi@example.test", encoder.encode("Passw0rd123"), CLOCK.instant());
        ReflectionTestUtils.setField(user, "id", 7L);
        when(users.findByEmail("budi@example.test")).thenReturn(Optional.of(user));
        when(users.findByEmail("nobody@example.test")).thenReturn(Optional.empty());
        return user;
    }

    @Test
    void login_wrong_password_and_unknown_email_return_identical_401() {
        existingUser();

        AppException wrongPassword = catchAppException(() -> service.login(new LoginRequest("budi@example.test", "Wr0ngPassword")));
        AppException unknownEmail = catchAppException(() -> service.login(new LoginRequest("nobody@example.test", "Passw0rd123")));

        assertThat(wrongPassword.code()).isEqualTo(ErrorCode.UNAUTHENTICATED);
        assertThat(unknownEmail.code()).isEqualTo(wrongPassword.code());
        assertThat(unknownEmail.getMessage()).isEqualTo(wrongPassword.getMessage());
        verify(refreshTokens, never()).issue(7L);
    }

    @Test
    void login_with_password_over_72_bytes_is_the_same_generic_401() {
        existingUser();
        String tooLong = "Passw0rd123" + "x".repeat(70);

        AppException ex = catchAppException(() -> service.login(new LoginRequest("budi@example.test", tooLong)));

        assertThat(ex.code()).isEqualTo(ErrorCode.UNAUTHENTICATED);
    }

    @Test
    void login_success_returns_tokens_with_15_minute_expiry() {
        existingUser();
        when(refreshTokens.issue(7L)).thenReturn("raw-refresh");

        AuthTokens tokens = service.login(new LoginRequest(" Budi@Example.test", "Passw0rd123"));

        assertThat(tokens.accessToken()).isNotBlank();
        assertThat(tokens.expiresInSeconds()).isEqualTo(900);
        assertThat(tokens.refreshToken()).isEqualTo("raw-refresh");
        assertThat(tokens.toString()).doesNotContain("raw-refresh");
    }

    @Test
    void refresh_with_missing_token_is_401_and_touches_nothing() {
        assertThat(catchAppException(() -> service.refresh(null)).code()).isEqualTo(ErrorCode.UNAUTHENTICATED);
        assertThat(catchAppException(() -> service.refresh("  ")).code()).isEqualTo(ErrorCode.UNAUTHENTICATED);
        verify(refreshTokens, never()).consume(any());
    }

    @Test
    void logout_without_cookie_is_a_noop_and_with_cookie_revokes() {
        service.logout(null);
        verify(refreshTokens, never()).revoke(any());
        service.logout("raw");
        verify(refreshTokens).revoke("raw");
    }

    @Test
    void revokeAll_delegates_to_refresh_token_service() {
        service.revokeAll(7L);
        verify(refreshTokens).revokeAll(7L);
    }

    private static AppException catchAppException(Runnable r) {
        try {
            r.run();
        } catch (AppException e) {
            return e;
        }
        throw new AssertionError("AppException expected");
    }
}
