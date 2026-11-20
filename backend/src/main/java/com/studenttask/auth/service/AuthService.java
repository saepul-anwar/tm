package com.studenttask.auth.service;

import com.studenttask.auth.dto.AuthTokens;
import com.studenttask.auth.dto.LoginRequest;
import com.studenttask.auth.dto.RegisterRequest;
import com.studenttask.auth.dto.UserProfileResponse;
import com.studenttask.common.error.AppException;
import com.studenttask.common.error.ErrorCode;
import com.studenttask.user.entity.ReminderSettings;
import com.studenttask.user.entity.User;
import com.studenttask.user.mapper.UserMapper;
import com.studenttask.user.repository.ReminderSettingsRepository;
import com.studenttask.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final int BCRYPT_MAX_BYTES = 72;
    private static final String DUMMY_PASSWORD = "dummy-password-for-timing-equalisation";

    private final UserRepository users;
    private final ReminderSettingsRepository reminderSettings;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokens;
    private final UserMapper userMapper;
    private final Clock clock;
    /** Compared against when the email is unknown so both failure paths cost one BCrypt check. */
    private final String dummyHash;

    public AuthService(
            UserRepository users,
            ReminderSettingsRepository reminderSettings,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            RefreshTokenService refreshTokens,
            UserMapper userMapper,
            Clock clock) {
        this.users = users;
        this.reminderSettings = reminderSettings;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokens = refreshTokens;
        this.userMapper = userMapper;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode(DUMMY_PASSWORD);
    }

    @Transactional
    public UserProfileResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (users.existsByEmail(email)) {
            throw emailTaken();
        }
        Instant now = clock.instant();
        User user = new User(request.fullName().trim(), email, passwordEncoder.encode(request.password()), now);
        try {
            user = users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw emailTaken(); // concurrent registration of the same email
        }
        reminderSettings.save(ReminderSettings.allEnabled(user.getId(), now));
        return userMapper.toProfile(user);
    }

    /**
     * Deliberately not {@code @Transactional}: BCrypt is deliberately slow and must not pin a database connection
     * inside a transaction. The user lookup runs in the repository's own short transaction and only
     * {@link RefreshTokenService#issue} (token persistence) is transactional.
     */
    public AuthTokens login(LoginRequest request) {
        User user = users.findByEmail(normalizeEmail(request.email())).orElse(null);
        String hash = user != null ? user.getPasswordHash() : dummyHash;
        boolean passwordOk = fitsBcrypt(request.password()) && passwordEncoder.matches(request.password(), hash);
        if (user == null || !passwordOk) {
            throw new AppException(ErrorCode.UNAUTHENTICATED, "Email atau kata sandi salah.");
        }
        return issueTokens(user, refreshTokens.issue(user.getId()));
    }

    @Transactional(noRollbackFor = AppException.class)
    public AuthTokens refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new AppException(ErrorCode.UNAUTHENTICATED, "Sesi tidak valid. Silakan masuk kembali.");
        }
        long userId = refreshTokens.consume(rawRefreshToken);
        User user = users.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED, "Sesi tidak valid. Silakan masuk kembali."));
        return issueTokens(user, refreshTokens.issue(userId));
    }

    public void logout(String rawRefreshToken) {
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            refreshTokens.revoke(rawRefreshToken);
        }
    }

    /** Revokes every active refresh token of the user (password change, reuse detection). */
    public void revokeAll(long userId) {
        refreshTokens.revokeAll(userId);
    }

    @Transactional(readOnly = true)
    public UserProfileResponse me(long userId) {
        return users.findById(userId)
                .map(userMapper::toProfile)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED, "Autentikasi diperlukan."));
    }

    private AuthTokens issueTokens(User user, String refreshToken) {
        String access = jwtService.issue(user.getId(), user.getEmail());
        return new AuthTokens(access, JwtService.ACCESS_TOKEN_TTL.toSeconds(), refreshToken);
    }

    private static boolean fitsBcrypt(String password) {
        return password.getBytes(StandardCharsets.UTF_8).length <= BCRYPT_MAX_BYTES;
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static AppException emailTaken() {
        return new AppException(ErrorCode.CONFLICT, "Email sudah terdaftar.");
    }
}
