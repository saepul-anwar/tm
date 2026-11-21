package com.studenttask.telegram.repository;

import com.studenttask.telegram.entity.TelegramLinkToken;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TelegramLinkTokenRepository extends JpaRepository<TelegramLinkToken, Long> {

    Optional<TelegramLinkToken> findByTokenHash(String tokenHash);

    /** Invalidates every not-yet-used token of the user (new connect request or disconnect). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from TelegramLinkToken t where t.userId = :userId and t.usedAt is null")
    int deleteUnusedByUserId(@Param("userId") long userId);

    /**
     * Atomic single-use claim: exactly one concurrent caller gets 1; everyone else (used, expired, deleted) gets 0.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update TelegramLinkToken t set t.usedAt = :now where t.id = :id and t.usedAt is null and t.expiresAt > :now")
    int claim(@Param("id") long id, @Param("now") Instant now);
}
