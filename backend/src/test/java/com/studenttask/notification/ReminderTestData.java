package com.studenttask.notification;

import com.studenttask.notification.entity.NotificationStatus;
import com.studenttask.notification.entity.ReminderType;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.jdbc.core.JdbcTemplate;

/** Synthetic rows for reminder tests (plain SQL so the tests do not depend on the code under test). */
final class ReminderTestData {

    static final Instant T0 = Instant.parse("2026-11-20T10:00:00Z");

    private final JdbcTemplate jdbc;

    ReminderTestData(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    static LocalDateTime utc(Instant i) {
        return LocalDateTime.ofInstant(i, ZoneOffset.UTC);
    }

    long user(Long chatId, String timezone) {
        LocalDateTime now = utc(T0);
        jdbc.update("insert into users (full_name, email, password_hash, telegram_chat_id, timezone, created_at, updated_at)"
                + " values (?, ?, ?, ?, ?, ?, ?)", "Sinta Synthetic", "rem-" + UUID.randomUUID() + "@example.test",
                "$2a$10$syntheticsyntheticsyntheticsyntheticsyntheticsyn", chatId, timezone, now, now);
        return jdbc.queryForObject("select max(id) from users", Long.class);
    }

    long connectedUser() {
        return user(ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_000_000_000_000L), "Asia/Jakarta");
    }

    long task(long userId, String title, Instant deadline, String status) {
        LocalDateTime now = utc(T0);
        jdbc.update("insert into tasks (user_id, title, subject, deadline, priority, status, created_at, updated_at)"
                + " values (?, ?, 'Algoritma', ?, 'HIGH', ?, ?, ?)", userId, title, utc(deadline), status, now, now);
        return jdbc.queryForObject("select max(id) from tasks", Long.class);
    }

    long log(long userId, long taskId, ReminderType type, Instant scheduledAt, NotificationStatus status,
            int attempts, Instant nextRetryAt, Instant lockedAt) {
        LocalDateTime now = utc(T0);
        jdbc.update("insert into notification_logs (user_id, task_id, reminder_type, scheduled_at, status, attempts,"
                + " next_retry_at, locked_at, created_at, updated_at) values (?,?,?,?,?,?,?,?,?,?)", userId, taskId,
                type.name(), utc(scheduledAt), status.name(), attempts,
                nextRetryAt == null ? null : utc(nextRetryAt), lockedAt == null ? null : utc(lockedAt), now, now);
        return jdbc.queryForObject("select max(id) from notification_logs", Long.class);
    }

    Map<String, Object> row(long logId) {
        return jdbc.queryForMap("select * from notification_logs where id = ?", logId);
    }

    String status(long logId) {
        return jdbc.queryForObject("select status from notification_logs where id = ?", String.class, logId);
    }

    int attempts(long logId) {
        return jdbc.queryForObject("select attempts from notification_logs where id = ?", Integer.class, logId);
    }

    Instant instantOf(long logId, String column) {
        Timestamp ts = jdbc.queryForObject("select " + column + " from notification_logs where id = ?",
                (rs, n) -> rs.getTimestamp(1, java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))),
                logId);
        return ts == null ? null : ts.toInstant();
    }

    int countLogs(long taskId) {
        return jdbc.queryForObject("select count(*) from notification_logs where task_id = ?", Integer.class, taskId);
    }

    /** Removes everything the scheduler could pick up (other test classes share the database). */
    void deleteAll() {
        jdbc.update("delete from notification_logs");
        jdbc.update("delete from tasks");
        jdbc.update("delete from users");
    }

    void settings(long userId, boolean h24, boolean h6, boolean h1, boolean atDeadline) {
        LocalDateTime now = utc(T0);
        jdbc.update("insert into reminder_settings (user_id, reminder_24_hours, reminder_6_hours, reminder_1_hour,"
                + " reminder_at_deadline, created_at, updated_at) values (?,?,?,?,?,?,?)", userId, h24, h6, h1,
                atDeadline, now, now);
    }

    void setTaskStatus(long taskId, String status) {
        jdbc.update("update tasks set status = ? where id = ?", status, taskId);
    }

    void setDeadline(long taskId, Instant deadline) {
        jdbc.update("update tasks set deadline = ? where id = ?", utc(deadline), taskId);
    }

    void deleteTask(long taskId) {
        jdbc.update("delete from tasks where id = ?", taskId);
    }

    void disconnectTelegram(long userId) {
        jdbc.update("update users set telegram_chat_id = null, telegram_connected_at = null where id = ?", userId);
    }

    void setTimezone(long userId, String timezone) {
        jdbc.update("update users set timezone = ? where id = ?", timezone, userId);
    }

    /** Bulk insert of TODO tasks for one user, all with the same deadline. */
    void tasks(long userId, int count, Instant deadline) {
        LocalDateTime now = utc(T0);
        jdbc.batchUpdate("insert into tasks (user_id, title, subject, deadline, priority, status, created_at, updated_at)"
                + " values (?, ?, 'Algoritma', ?, 'HIGH', 'TODO', ?, ?)", java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> new Object[] {userId, "Tugas Sintetis " + i, utc(deadline), now, now}).toList());
    }

    int countSent() {
        return jdbc.queryForObject("select count(*) from notification_logs where status = 'SENT'", Integer.class);
    }

    int countLogs() {
        return jdbc.queryForObject("select count(*) from notification_logs", Integer.class);
    }

    /** Bulk insert of TODO tasks for one user with deadlines first, first+step, first+2*step, ... */
    void spreadTasks(long userId, int count, Instant first, java.time.Duration step) {
        LocalDateTime now = utc(T0);
        jdbc.batchUpdate("insert into tasks (user_id, title, subject, deadline, priority, status, created_at, updated_at)"
                + " values (?, ?, 'Algoritma', ?, 'HIGH', ?, ?, ?)", java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> new Object[] {userId, "Tugas Sintetis " + i, utc(first.plus(step.multipliedBy(i))),
                        i % 4 == 0 ? "COMPLETED" : "TODO", now, now}).toList());
    }
}
