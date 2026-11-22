package com.studenttask.notification.dto;

import com.studenttask.notification.entity.ReminderType;
import java.time.Instant;

/** A reminder that is due: the (task, type, scheduledAt) triple is the idempotency key. */
public record ReminderCandidate(long taskId, long userId, ReminderType type, Instant scheduledAt) {
}
