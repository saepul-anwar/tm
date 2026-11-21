package com.studenttask.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.studenttask.auth.service.AuthService;
import com.studenttask.common.error.AppException;
import com.studenttask.common.error.ErrorCode;
import com.studenttask.security.UserRateLimiter;
import com.studenttask.user.dto.ProfileResponse;
import com.studenttask.user.dto.UpdateProfileRequest;
import com.studenttask.user.entity.User;
import com.studenttask.user.mapper.ProfileMapper;
import com.studenttask.user.repository.UserRepository;
import com.studenttask.user.service.ProfileService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

class ProfileServiceTest {

    private static final class MutableClock extends Clock {
        private Instant now = NOW;

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private static final Instant NOW = Instant.parse("2026-11-20T00:00:00Z");
    private static final String PASSWORD = "Passw0rd-Synthetic1";

    private final UserRepository users = mock(UserRepository.class);
    private final AuthService auth = mock(AuthService.class);
    private final TransactionTemplate tx = mock(TransactionTemplate.class);
    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final MutableClock clock = new MutableClock();
    private final UserRateLimiter limiter = new UserRateLimiter(clock);
    private ProfileService service;
    private User user;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        user = new User("Budi Santoso", "budi@example.test", encoder.encode(PASSWORD), NOW);
        ReflectionTestUtils.setField(user, "id", 7L);
        when(users.findById(7L)).thenReturn(Optional.of(user));
        when(users.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tx.execute(any())).thenAnswer(inv -> ((TransactionCallback<Object>) inv.getArgument(0))
                .doInTransaction(null));
        service = new ProfileService(users, encoder, auth, new ProfileMapper(), tx, clock, limiter);
    }

    private static UpdateProfileRequest req(String name, String email, String tz, String current, String next) {
        return new UpdateProfileRequest(name, email, tz, current, next);
    }

    private static void assertField(Runnable action, ErrorCode code, String field) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(AppException.class, e -> {
            assertThat(e.code()).isEqualTo(code);
            assertThat(e.details()).extracting("field").contains(field);
        });
    }

    @Test
    void null_fields_leave_the_user_unchanged() {
        ProfileResponse r = service.update(7L, req(null, null, null, null, null));
        assertThat(r.fullName()).isEqualTo("Budi Santoso");
        assertThat(r.email()).isEqualTo("budi@example.test");
        assertThat(r.timezone()).isEqualTo("Asia/Jakarta");
        verify(auth, never()).revokeAll(anyLong());
    }

    @Test
    void name_is_trimmed_and_blank_or_too_long_is_rejected() {
        assertThat(service.update(7L, req("  Siti  ", null, null, null, null)).fullName()).isEqualTo("Siti");
        assertField(() -> service.update(7L, req("   ", null, null, null, null)), ErrorCode.VALIDATION_ERROR, "fullName");
        assertField(() -> service.update(7L, req("x".repeat(101), null, null, null, null)),
                ErrorCode.VALIDATION_ERROR, "fullName");
    }

    @Test
    void timezone_must_be_a_region_id() {
        assertThat(service.update(7L, req(null, null, "Europe/Paris", null, null)).timezone())
                .isEqualTo("Europe/Paris");
        for (String bad : new String[] {"+07:00", "UTC+7", "Nowhere/Land", "", "Z"}) {
            assertField(() -> service.update(7L, req(null, null, bad, null, null)),
                    ErrorCode.VALIDATION_ERROR, "timezone");
        }
    }

    @Test
    void email_change_needs_current_password_and_is_normalised() {
        assertField(() -> service.update(7L, req(null, "new@example.test", null, null, null)),
                ErrorCode.VALIDATION_ERROR, "currentPassword");
        assertField(() -> service.update(7L, req(null, "new@example.test", null, "Wr0ng-Password", null)),
                ErrorCode.VALIDATION_ERROR, "currentPassword");
        assertThat(service.update(7L, req(null, "New@Example.TEST", null, PASSWORD, null)).email())
                .isEqualTo("new@example.test");
    }

    @Test
    void same_email_is_a_noop_without_password() {
        assertThat(service.update(7L, req(null, "BUDI@example.test", null, null, null)).email())
                .isEqualTo("budi@example.test");
        verify(users, never()).existsByEmailAndIdNot(anyString(), anyLong());
    }

    @Test
    void email_used_by_another_user_is_a_conflict_including_a_concurrent_insert() {
        when(users.existsByEmailAndIdNot("taken@example.test", 7L)).thenReturn(true);
        assertThatThrownBy(() -> service.update(7L, req(null, "taken@example.test", null, PASSWORD, null)))
                .isInstanceOfSatisfying(AppException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.CONFLICT));

        when(users.saveAndFlush(any(User.class))).thenThrow(new DataIntegrityViolationException("dup"));
        assertThatThrownBy(() -> service.update(7L, req(null, "race@example.test", null, PASSWORD, null)))
                .isInstanceOfSatisfying(AppException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.CONFLICT));
    }

    @Test
    void password_change_hashes_revokes_and_enforces_policy() {
        String oldHash = user.getPasswordHash();
        assertField(() -> service.update(7L, req(null, null, null, null, "N3w-Synthetic-Pass")),
                ErrorCode.VALIDATION_ERROR, "currentPassword");
        assertField(() -> service.update(7L, req(null, null, null, PASSWORD, "weak")),
                ErrorCode.VALIDATION_ERROR, "newPassword");
        verify(auth, never()).revokeAll(anyLong());
        assertThat(user.getPasswordHash()).isEqualTo(oldHash);

        service.update(7L, req(null, null, null, PASSWORD, "N3w-Synthetic-Pass"));
        assertThat(encoder.matches("N3w-Synthetic-Pass", user.getPasswordHash())).isTrue();
        assertThat(user.getPasswordHash()).isNotEqualTo("N3w-Synthetic-Pass");
        verify(auth).revokeAll(7L);
    }

    @Test
    void wrong_current_password_longer_than_72_bytes_is_rejected_without_truncation() {
        assertField(() -> service.update(7L, req(null, null, null, PASSWORD + "x".repeat(70), "N3w-Synthetic-Pass")),
                ErrorCode.VALIDATION_ERROR, "currentPassword");
    }

    @Test
    void unknown_user_is_unauthenticated() {
        when(users.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(99L)).isInstanceOfSatisfying(AppException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.UNAUTHENTICATED));
    }

    // ---- F3: failed currentPassword budget and token revocation on email change ----

    private static final String WRONG = "Wr0ng-Password";

    private void failEmailChange(String guess) {
        assertField(() -> service.update(7L, req(null, "new@example.test", null, guess, null)),
                ErrorCode.VALIDATION_ERROR, "currentPassword");
    }

    private void assertRateLimited(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(AppException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.RATE_LIMITED));
    }

    @Test
    void sixth_wrong_current_password_within_15_minutes_is_rate_limited_even_if_the_guess_is_right() {
        for (int i = 0; i < 5; i++) {
            failEmailChange(WRONG);
        }

        assertRateLimited(() -> service.update(7L, req(null, "new@example.test", null, WRONG, null)));
        assertRateLimited(() -> service.update(7L, req(null, "new@example.test", null, PASSWORD, null)));
        assertRateLimited(() -> service.update(7L, req(null, null, null, PASSWORD, "N3w-Synthetic-Pass")));
        assertThat(user.getEmail()).isEqualTo("budi@example.test");
        verify(auth, never()).revokeAll(anyLong());
    }

    @Test
    void failed_attempt_budget_is_per_user() {
        User other = new User("Siti Synthetic", "siti@example.test", encoder.encode(PASSWORD), NOW);
        ReflectionTestUtils.setField(other, "id", 8L);
        when(users.findById(8L)).thenReturn(Optional.of(other));
        for (int i = 0; i < 5; i++) {
            failEmailChange(WRONG);
        }

        assertThat(service.update(8L, req(null, "siti2@example.test", null, PASSWORD, null)).email())
                .isEqualTo("siti2@example.test");
    }

    @Test
    void budget_returns_after_the_15_minute_window() {
        for (int i = 0; i < 5; i++) {
            failEmailChange(WRONG);
        }
        clock.advance(Duration.ofMinutes(14));
        assertRateLimited(() -> service.update(7L, req(null, "new@example.test", null, PASSWORD, null)));

        clock.advance(Duration.ofMinutes(1));

        assertThat(service.update(7L, req(null, "new@example.test", null, PASSWORD, null)).email())
                .isEqualTo("new@example.test");
    }

    @Test
    void successful_verification_does_not_consume_budget() {
        for (int i = 0; i < 4; i++) {
            failEmailChange(WRONG);
        }
        for (int i = 0; i < 20; i++) {
            service.update(7L, req(null, "budi@example.test", null, PASSWORD, "N3w-Synthetic-Pass"));
            service.update(7L, req(null, null, null, "N3w-Synthetic-Pass", PASSWORD));
        }

        failEmailChange(WRONG); // 5th failure still gets a normal 400
        assertRateLimited(() -> service.update(7L, req(null, "new@example.test", null, PASSWORD, null)));
    }

    @Test
    void missing_current_password_is_not_a_guess_and_name_or_timezone_updates_never_need_the_budget() {
        for (int i = 0; i < 10; i++) {
            failEmailChange(null);
        }
        for (int i = 0; i < 5; i++) {
            failEmailChange(WRONG);
        }

        assertThat(service.update(7L, req("Nama Baru", null, "Europe/Paris", null, null)).fullName())
                .isEqualTo("Nama Baru");
    }

    @Test
    void email_change_revokes_all_refresh_tokens_but_a_rejected_change_does_not() {
        when(users.existsByEmailAndIdNot("taken@example.test", 7L)).thenReturn(true);
        assertThatThrownBy(() -> service.update(7L, req(null, "taken@example.test", null, PASSWORD, null)))
                .isInstanceOf(AppException.class);
        verify(auth, never()).revokeAll(anyLong());

        service.update(7L, req(null, "new@example.test", null, PASSWORD, null));

        verify(auth).revokeAll(7L);
    }

    @Test
    void name_or_timezone_only_update_does_not_revoke_refresh_tokens() {
        service.update(7L, req("Nama Baru", null, "Europe/Paris", null, null));

        verify(auth, never()).revokeAll(anyLong());
    }

    // ---- F4: the hash must be unchanged between verification and write for email changes too ----

    @Test
    void email_change_is_a_conflict_when_the_password_hash_changed_between_verification_and_write() {
        User changed = new User("Budi Santoso", "budi@example.test", encoder.encode("Another-Passw0rd-1"), NOW);
        ReflectionTestUtils.setField(changed, "id", 7L);
        when(users.findById(7L)).thenReturn(Optional.of(user), Optional.of(changed));

        assertThatThrownBy(() -> service.update(7L, req(null, "new@example.test", null, PASSWORD, null)))
                .isInstanceOfSatisfying(AppException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.CONFLICT));

        assertThat(changed.getEmail()).isEqualTo("budi@example.test");
        verify(users, never()).saveAndFlush(any(User.class));
        verify(auth, never()).revokeAll(anyLong());
    }
}
