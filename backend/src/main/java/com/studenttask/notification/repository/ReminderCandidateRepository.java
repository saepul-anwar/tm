package com.studenttask.notification.repository;

import com.studenttask.notification.dto.ReminderCandidate;
import com.studenttask.notification.entity.ReminderType;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The scheduler's candidate query (spec section 10, step 1): one indexed query per reminder type. Plain SQL through
 * {@link JdbcTemplate} because of the JOIN / NOT EXISTS shape; every value is a bound parameter. The only text that
 * differs per type is the settings column, taken from a fixed switch over the enum (never from input).
 *
 * <p>{@code scheduled_at = deadline - offset} must satisfy {@code windowStart < scheduled_at <= now}. The range is
 * written on the plain {@code deadline} column ({@code windowStart + offset < deadline <= now + offset}) so that
 * {@code ix_tasks_status_deadline} can be used; the function of a column would force a scan.
 *
 * <p>"Not completed" is written as {@code status IN ('TODO','IN_PROGRESS')}, the same set as {@code <> 'COMPLETED'}
 * under {@code ck_tasks_status}. With {@code <>} the optimizer measurably chose a full index scan on
 * {@code ix_tasks_user_status_deadline} and a materialized NOT EXISTS (see docs/DATABASE_DESIGN.md); the IN list gives
 * a range on {@code ix_tasks_status_deadline} and a unique-key probe per task. A new status value would need to be
 * added here (the candidate tests cover all current ones).
 *
 * <p>A user without a {@code reminder_settings} row has never changed a toggle, so the table defaults (all on) apply:
 * LEFT JOIN plus {@code COALESCE(column, TRUE)}.
 */
@Repository
public class ReminderCandidateRepository {

    private static final String TEMPLATE = """
            SELECT t.id, t.user_id, t.deadline
            FROM tasks t
            JOIN users u ON u.id = t.user_id
            LEFT JOIN reminder_settings s ON s.user_id = u.id
            WHERE t.status IN ('TODO', 'IN_PROGRESS')
              AND t.deadline > ? AND t.deadline <= ?
              AND u.telegram_chat_id IS NOT NULL
              AND COALESCE(s.%s, TRUE)
              AND NOT EXISTS (
                SELECT 1 FROM notification_logs n
                WHERE n.task_id = t.id AND n.reminder_type = ?
                  AND n.scheduled_at = DATE_SUB(t.deadline, INTERVAL ? SECOND))
            ORDER BY t.deadline, t.id
            LIMIT ?""";

    private final JdbcTemplate jdbc;

    public ReminderCandidateRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Due reminders of {@code type} with {@code windowStart < scheduledAt <= now}, oldest first, at most {@code limit}. */
    public List<ReminderCandidate> findCandidates(ReminderType type, Instant windowStart, Instant now, int limit) {
        return jdbc.query(candidateSql(type), (rs, row) -> {
            Instant deadline = rs.getObject("deadline", LocalDateTime.class).toInstant(ZoneOffset.UTC);
            return new ReminderCandidate(rs.getLong("id"), rs.getLong("user_id"), type,
                    deadline.minus(type.offset()));
        }, bindingsFor(type, windowStart, now, limit));
    }

    /** The SQL text for a type (public so the plan can be inspected with EXPLAIN in tests). */
    public String candidateSql(ReminderType type) {
        return TEMPLATE.formatted(settingsColumn(type));
    }

    /** Parameter values for {@link #candidateSql}, in order. */
    public Object[] bindingsFor(ReminderType type, Instant windowStart, Instant now, int limit) {
        return new Object[] {utc(windowStart.plus(type.offset())), utc(now.plus(type.offset())), type.name(),
                type.offset().toSeconds(), limit};
    }

    private static String settingsColumn(ReminderType type) {
        return switch (type) {
            case H24 -> "reminder_24_hours";
            case H6 -> "reminder_6_hours";
            case H1 -> "reminder_1_hour";
            case AT_DEADLINE -> "reminder_at_deadline";
        };
    }

    private static LocalDateTime utc(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
