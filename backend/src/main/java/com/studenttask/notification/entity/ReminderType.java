package com.studenttask.notification.entity;

import java.time.Duration;

/** Reminder kinds; {@code scheduled_at = deadline - offset()}. */
public enum ReminderType {
    H24(Duration.ofHours(24)),
    H6(Duration.ofHours(6)),
    H1(Duration.ofHours(1)),
    AT_DEADLINE(Duration.ZERO);

    private final Duration offset;

    ReminderType(Duration offset) {
        this.offset = offset;
    }

    public Duration offset() {
        return offset;
    }
}
