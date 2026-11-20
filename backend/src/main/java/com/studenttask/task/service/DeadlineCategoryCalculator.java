package com.studenttask.task.service;

import com.studenttask.task.entity.DeadlineCategory;
import com.studenttask.task.entity.TaskStatus;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** Pure function (no I/O, no clock): all inputs are passed in so boundaries are testable. */
public final class DeadlineCategoryCalculator {

    /** Length of the "upcoming" window, shared with the dashboard queries. */
    public static final Duration UPCOMING_WINDOW = Duration.ofDays(7);

    private DeadlineCategoryCalculator() {
    }

    /**
     * Precedence: COMPLETED gives null; deadline strictly before now is OVERDUE (deadline == now is not);
     * then local date of deadline in {@code zone} equal to today / tomorrow; then UPCOMING up to and
     * including now + 7 days; else LATER.
     */
    public static DeadlineCategory categorize(Instant deadline, TaskStatus status, ZoneId zone, Instant now) {
        if (status == TaskStatus.COMPLETED) {
            return null;
        }
        if (deadline.isBefore(now)) {
            return DeadlineCategory.OVERDUE;
        }
        LocalDate today = now.atZone(zone).toLocalDate();
        LocalDate deadlineDate = deadline.atZone(zone).toLocalDate();
        if (deadlineDate.equals(today)) {
            return DeadlineCategory.DUE_TODAY;
        }
        if (deadlineDate.equals(today.plusDays(1))) {
            return DeadlineCategory.DUE_TOMORROW;
        }
        return deadline.isAfter(now.plus(UPCOMING_WINDOW)) ? DeadlineCategory.LATER : DeadlineCategory.UPCOMING;
    }
}
