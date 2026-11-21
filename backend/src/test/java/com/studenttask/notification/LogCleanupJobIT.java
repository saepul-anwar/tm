package com.studenttask.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.studenttask.MySqlTestBase;
import com.studenttask.config.AppProperties;
import com.studenttask.scheduler.LogCleanupJob;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Real MySQL, injected clock, synthetic rows only. */
class LogCleanupJobIT extends MySqlTestBase {

    private static final Instant NOW = ReminderTestData.T0;

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    AppProperties props;

    ReminderTestData data;
    MutableClock clock;
    long user;
    long task;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(NOW);
        data = new ReminderTestData(jdbc);
        data.deleteAll();
        jdbc.update("delete from telegram_link_tokens");
        jdbc.update("delete from refresh_tokens");
        user = data.connectedUser();
        task = data.task(user, "Tugas", NOW.plus(Duration.ofDays(30)), "TODO");
    }

    @AfterEach
    void tearDown() {
        data.deleteAll();
    }

    private static LocalDateTime ago(Duration d) {
        return ReminderTestData.utc(NOW.minus(d));
    }

    private static LocalDateTime ahead(Duration d) {
        return ReminderTestData.utc(NOW.plus(d));
    }

    private long log(Duration age, int minuteOffset) {
        LocalDateTime created = ago(age);
        jdbc.update("insert into notification_logs (user_id, task_id, reminder_type, scheduled_at, status, attempts,"
                + " created_at, updated_at) values (?, ?, 'H1', ?, 'SENT', 1, ?, ?)", user, task,
                ReminderTestData.utc(NOW.plus(Duration.ofMinutes(minuteOffset))), created, created);
        return jdbc.queryForObject("select max(id) from notification_logs", Long.class);
    }

    private long linkToken(LocalDateTime expires, LocalDateTime used) {
        String hash = (UUID.randomUUID() + "" + UUID.randomUUID()).replace("-", "").substring(0, 64);
        jdbc.update("insert into telegram_link_tokens (user_id, token_hash, expires_at, used_at, created_at)"
                + " values (?, ?, ?, ?, ?)", user, hash, expires, used, ago(Duration.ofDays(3)));
        return jdbc.queryForObject("select max(id) from telegram_link_tokens", Long.class);
    }

    private long refreshToken(LocalDateTime expires, LocalDateTime revoked) {
        String hash = (UUID.randomUUID() + "" + UUID.randomUUID()).replace("-", "").substring(0, 64);
        jdbc.update("insert into refresh_tokens (user_id, token_hash, expires_at, revoked_at, created_at)"
                + " values (?, ?, ?, ?, ?)", user, hash, expires, revoked, ago(Duration.ofDays(20)));
        return jdbc.queryForObject("select max(id) from refresh_tokens", Long.class);
    }

    private boolean exists(String table, long id) {
        return jdbc.queryForObject("select count(*) from " + table + " where id = ?", Integer.class, id) == 1;
    }

    private LogCleanupJob job(int batchSize) {
        AppProperties.Reminder r = props.reminder();
        AppProperties.Reminder small = new AppProperties.Reminder(r.enabled(), r.pollInterval(), r.catchUpWindow(),
                r.batchSize(), r.sendingTimeout(), r.maxAttempts(), r.retryBackoff(), r.logRetentionDays(),
                r.sendPause(), r.cleanupCron(), batchSize);
        return new LogCleanupJob(jdbc, new AppProperties(props.jwt(), props.telegram(), small, props.cors(),
                props.auth()), clock);
    }

    @Test
    void cleanup_deletes_only_old_rows() {
        long oldLog = log(Duration.ofDays(91), 1);
        long recentLog = log(Duration.ofDays(89), 2);
        long freshLog = log(Duration.ZERO, 3);

        long expiredLink = linkToken(ago(Duration.ofMinutes(1)), null);
        long liveLink = linkToken(ahead(Duration.ofMinutes(5)), null);
        long usedOldLink = linkToken(ahead(Duration.ofMinutes(5)), ago(Duration.ofDays(1).plusHours(1)));
        long usedRecentLink = linkToken(ahead(Duration.ofMinutes(5)), ago(Duration.ofHours(23)));

        long expiredRefresh = refreshToken(ago(Duration.ofSeconds(1)), null);
        long liveRefresh = refreshToken(ahead(Duration.ofDays(3)), null);
        long revokedOldRefresh = refreshToken(ahead(Duration.ofDays(3)), ago(Duration.ofDays(8)));
        long revokedRecentRefresh = refreshToken(ahead(Duration.ofDays(3)), ago(Duration.ofDays(6)));

        LogCleanupJob.Result result = job(1000).run();

        assertThat(result).isEqualTo(new LogCleanupJob.Result(1, 2, 2));
        assertThat(exists("notification_logs", oldLog)).isFalse();
        assertThat(exists("notification_logs", recentLog)).isTrue();
        assertThat(exists("notification_logs", freshLog)).isTrue();
        assertThat(exists("telegram_link_tokens", expiredLink)).isFalse();
        assertThat(exists("telegram_link_tokens", usedOldLink)).isFalse();
        assertThat(exists("telegram_link_tokens", liveLink)).isTrue();
        assertThat(exists("telegram_link_tokens", usedRecentLink)).isTrue();
        assertThat(exists("refresh_tokens", expiredRefresh)).isFalse();
        assertThat(exists("refresh_tokens", revokedOldRefresh)).isFalse();
        assertThat(exists("refresh_tokens", liveRefresh)).isTrue();
        assertThat(exists("refresh_tokens", revokedRecentRefresh)).isTrue();
    }

    @Test
    void deletes_in_batches_until_nothing_old_is_left() {
        for (int i = 0; i < 7; i++) {
            log(Duration.ofDays(100), i);
            linkToken(ago(Duration.ofHours(1)), null);
            refreshToken(ago(Duration.ofHours(1)), null);
        }
        long keep = log(Duration.ofDays(1), 50);

        LogCleanupJob.Result result = job(3).run();

        assertThat(result).isEqualTo(new LogCleanupJob.Result(7, 7, 7));
        assertThat(exists("notification_logs", keep)).isTrue();
        assertThat(job(3).run()).isEqualTo(new LogCleanupJob.Result(0, 0, 0));
    }

    @Test
    void retention_follows_the_configured_days() {
        long id = log(Duration.ofDays(91), 1);
        AppProperties.Reminder r = props.reminder();
        AppProperties.Reminder longer = new AppProperties.Reminder(r.enabled(), r.pollInterval(), r.catchUpWindow(),
                r.batchSize(), r.sendingTimeout(), r.maxAttempts(), r.retryBackoff(), 120, r.sendPause(),
                r.cleanupCron(), r.cleanupBatchSize());
        LogCleanupJob keepLonger = new LogCleanupJob(jdbc, new AppProperties(props.jwt(), props.telegram(), longer,
                props.cors(), props.auth()), clock);

        assertThat(keepLonger.run().notificationLogs()).isZero();
        assertThat(exists("notification_logs", id)).isTrue();
    }
}
