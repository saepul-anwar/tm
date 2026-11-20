package com.studenttask.auth.service;

import com.studenttask.auth.entity.RefreshToken;
import com.studenttask.auth.repository.RefreshTokenRepository;
import com.studenttask.common.TokenHasher;
import com.studenttask.common.error.AppException;
import com.studenttask.common.error.ErrorCode;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Opaque, single-use, rotating refresh tokens. Only SHA-256 hashes are persisted. */
@Service
public class RefreshTokenService {

    public static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(7);
    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository repository;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /** Creates a refresh token for the user and returns the raw value (shown once, never stored). */
    @Transactional
    public String issue(long userId) {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = clock.instant();
        repository.save(new RefreshToken(userId, TokenHasher.sha256Hex(raw), now.plus(REFRESH_TOKEN_TTL), now));
        return raw;
    }

    /**
     * Consumes the token exactly once and returns the owner id. Presenting a token that was already
     * rotated or revoked is treated as theft: every refresh token of that user is revoked. The revocation
     * must survive the exception, hence noRollbackFor.
     */
    @Transactional(noRollbackFor = AppException.class)
    public long consume(String raw) {
        RefreshToken token = repository.findByTokenHash(TokenHasher.sha256Hex(raw)).orElseThrow(this::invalid);
        Instant now = clock.instant();
        if (token.getRevokedAt() != null) {
            return reuseDetected(token.getUserId(), now);
        }
        if (!token.getExpiresAt().isAfter(now)) {
            throw invalid();
        }
        if (repository.revokeIfActive(token.getId(), now) == 0) {
            return reuseDetected(token.getUserId(), now); // lost a concurrent rotation race
        }
        return token.getUserId();
    }

    /** Idempotent: unknown or already revoked tokens are ignored. */
    @Transactional
    public void revoke(String raw) {
        repository.findByTokenHash(TokenHasher.sha256Hex(raw))
                .ifPresent(token -> repository.revokeIfActive(token.getId(), clock.instant()));
    }

    @Transactional
    public void revokeAll(long userId) {
        repository.revokeAllActiveForUser(userId, clock.instant());
    }

    private long reuseDetected(long userId, Instant now) {
        repository.revokeAllActiveForUser(userId, now);
        throw invalid();
    }

    private AppException invalid() {
        return new AppException(ErrorCode.UNAUTHENTICATED, "Sesi tidak valid. Silakan masuk kembali.");
    }
}
