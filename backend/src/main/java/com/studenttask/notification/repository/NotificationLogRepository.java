package com.studenttask.notification.repository;

import com.studenttask.notification.entity.NotificationLog;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every state change is one short conditional UPDATE (its own transaction). The caller learns from the affected-row
 * count whether it still owns the row; a stale worker gets 0 and must not act on the result.
 */
public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {

    /** Ids that look claimable; only a conditional {@link #claim} makes one of them owned. */
    @Query("select n.id from NotificationLog n where (n.status = com.studenttask.notification.entity.NotificationStatus.RETRY and n.nextRetryAt <= :now) "
            + "or (n.status = com.studenttask.notification.entity.NotificationStatus.SENDING and n.lockedAt < :staleBefore) order by n.id asc")
    List<Long> findClaimableIds(@Param("now") Instant now, @Param("staleBefore") Instant staleBefore, Pageable limit);

    /** Atomic take-over; conditions are re-checked inside the UPDATE, so exactly one worker gets 1. */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update NotificationLog n set n.status = com.studenttask.notification.entity.NotificationStatus.SENDING, n.lockedAt = :now, n.nextRetryAt = null, "
            + "n.attempts = n.attempts + 1, n.updatedAt = :now where n.id = :id and "
            + "((n.status = com.studenttask.notification.entity.NotificationStatus.RETRY and n.nextRetryAt <= :now) or (n.status = com.studenttask.notification.entity.NotificationStatus.SENDING and n.lockedAt < :staleBefore))")
    int claim(@Param("id") long id, @Param("now") Instant now, @Param("staleBefore") Instant staleBefore);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update NotificationLog n set n.status = com.studenttask.notification.entity.NotificationStatus.SENT, n.sentAt = :now, n.nextRetryAt = null, "
            + "n.errorMessage = null, n.updatedAt = :now "
            + "where n.id = :id and n.status = com.studenttask.notification.entity.NotificationStatus.SENDING and n.lockedAt = :lockedAt")
    int markSent(@Param("id") long id, @Param("lockedAt") Instant lockedAt, @Param("now") Instant now);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update NotificationLog n set n.status = com.studenttask.notification.entity.NotificationStatus.RETRY, n.nextRetryAt = :nextRetryAt, n.errorMessage = :error, "
            + "n.updatedAt = :now where n.id = :id and n.status = com.studenttask.notification.entity.NotificationStatus.SENDING and n.lockedAt = :lockedAt")
    int markRetry(@Param("id") long id, @Param("lockedAt") Instant lockedAt, @Param("nextRetryAt") Instant nextRetryAt,
            @Param("error") String error, @Param("now") Instant now);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update NotificationLog n set n.status = com.studenttask.notification.entity.NotificationStatus.FAILED, n.nextRetryAt = null, n.errorMessage = :error, "
            + "n.updatedAt = :now where n.id = :id and n.status = com.studenttask.notification.entity.NotificationStatus.SENDING and n.lockedAt = :lockedAt")
    int markFailed(@Param("id") long id, @Param("lockedAt") Instant lockedAt, @Param("error") String error,
            @Param("now") Instant now);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update NotificationLog n set n.status = com.studenttask.notification.entity.NotificationStatus.SKIPPED, n.nextRetryAt = null, n.errorMessage = :reason, "
            + "n.updatedAt = :now where n.id = :id and n.status = com.studenttask.notification.entity.NotificationStatus.SENDING and n.lockedAt = :lockedAt")
    int markSkipped(@Param("id") long id, @Param("lockedAt") Instant lockedAt, @Param("reason") String reason,
            @Param("now") Instant now);
}
