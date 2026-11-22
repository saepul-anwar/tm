package com.studenttask.scheduler;

import com.studenttask.config.AppProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Daily housekeeping: old notification logs, spent Telegram link tokens and dead refresh tokens. Each table is
 * deleted in LIMIT-bounded batches so no statement holds locks for long. Only counts are logged.
 */
@Component
public class LogCleanupJob {

    /** Number of rows deleted per table. */
    public record Result(int notificationLogs, int telegramLinkTokens, int refreshTokens) {
    }

    static final Duration USED_LINK_TOKEN_KEEP = Duration.ofDays(1);
    static final Duration REVOKED_REFRESH_TOKEN_KEEP = Duration.ofDays(7);
    private static final Logger LOG = LoggerFactory.getLogger(LogCleanupJob.class);

    private final JdbcTemplate jdbc;
    private final AppProperties.Reminder config;
    private final Clock clock;

    public LogCleanupJob(JdbcTemplate jdbc, AppProperties props, Clock clock) {
        this.jdbc = jdbc;
        this.config = props.reminder();
        this.clock = clock;
    }

    /** Cron in UTC; registered only while {@code app.reminder.enabled} is true (see {@link SchedulingConfig}). */
    @Scheduled(cron = "${app.reminder.cleanup-cron}", zone = "UTC")
    public void scheduledRun() {
        try {
            run();
        } catch (RuntimeException e) {
            LOG.warn("Cleanup failed: error={}", e.getClass().getSimpleName());
        }
    }

    public Result run() {
        Instant now = clock.instant();
        int logs = deleteInBatches("delete from notification_logs where created_at < ? limit ?",
                utc(now.minus(Duration.ofDays(config.logRetentionDays()))));
        int links = deleteInBatches("delete from telegram_link_tokens where expires_at < ?"
                + " or (used_at is not null and used_at < ?) limit ?", utc(now), utc(now.minus(USED_LINK_TOKEN_KEEP)));
        int refresh = deleteInBatches("delete from refresh_tokens where expires_at < ?"
                + " or (revoked_at is not null and revoked_at < ?) limit ?", utc(now),
                utc(now.minus(REVOKED_REFRESH_TOKEN_KEEP)));
        Result result = new Result(logs, links, refresh);
        if (logs + links + refresh > 0) {
            LOG.info("Cleanup deleted: notificationLogs={} telegramLinkTokens={} refreshTokens={}", logs, links, refresh);
        }
        return result;
    }

    /** Repeats the bounded DELETE until a batch comes back short; the last SQL placeholder is the batch size. */
    private int deleteInBatches(String sql, Object... conditionArgs) {
        Object[] args = java.util.Arrays.copyOf(conditionArgs, conditionArgs.length + 1);
        args[conditionArgs.length] = config.cleanupBatchSize();
        int total = 0;
        int deleted;
        do {
            deleted = jdbc.update(sql, args);
            total += deleted;
        } while (deleted == config.cleanupBatchSize());
        return total;
    }

    private static LocalDateTime utc(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
