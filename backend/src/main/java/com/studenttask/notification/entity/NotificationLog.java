package com.studenttask.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Read model of a reminder attempt. Rows are inserted by {@code ReminderClaimService} (native insert, see there) and
 * changed only through the conditional updates in {@code NotificationLogRepository}; this entity is never saved.
 */
@Entity
@Table(name = "notification_logs")
public class NotificationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private long userId;

    @Column(name = "task_id", nullable = false, updatable = false)
    private long taskId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reminder_type", nullable = false, updatable = false, columnDefinition = "varchar(16)")
    private ReminderType reminderType;

    @Column(name = "scheduled_at", nullable = false, updatable = false)
    private Instant scheduledAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(12)")
    private NotificationStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected NotificationLog() {
    }

    public Long getId() {
        return id;
    }

    public long getUserId() {
        return userId;
    }

    public long getTaskId() {
        return taskId;
    }

    public ReminderType getReminderType() {
        return reminderType;
    }

    public Instant getScheduledAt() {
        return scheduledAt;
    }

    public NotificationStatus getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getNextRetryAt() {
        return nextRetryAt;
    }

    public Instant getLockedAt() {
        return lockedAt;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
