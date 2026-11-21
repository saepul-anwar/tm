package com.studenttask.notification.service;

import com.studenttask.config.AppProperties;
import com.studenttask.notification.dto.ReminderCandidate;
import com.studenttask.notification.repository.NotificationLogRepository;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Anti-duplicate core. Ownership of a reminder row is decided by the database only: the unique key
 * {@code uk_nlogs_task_type_sched} for the first claim and a conditional UPDATE for retries/stale rows.
 *
 * <p>{@link #tryClaim} uses a plain JDBC insert in its own REQUIRES_NEW transaction instead of
 * {@code repository.save}: a duplicate-key error on a JPA flush would leave the Hibernate session inconsistent and
 * could mark a caller's transaction rollback-only. Here the failed statement only rolls back the tiny transaction
 * that the claim itself opened, and the exception is caught outside it. Plain {@code INSERT IGNORE} is not used
 * because it would also hide foreign-key and data errors.
 */
@Service
public class ReminderClaimService {

    private static final Logger LOG = LoggerFactory.getLogger(ReminderClaimService.class);
    private static final String INSERT = "insert into notification_logs (user_id, task_id, reminder_type, scheduled_at,"
            + " status, attempts, locked_at, created_at, updated_at) values (?, ?, ?, ?, 'SENDING', 1, ?, ?, ?)";

    private final NotificationLogRepository logs;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate newTransaction;
    private final AppProperties.Reminder config;
    private final Clock clock;

    public ReminderClaimService(NotificationLogRepository logs, JdbcTemplate jdbc, PlatformTransactionManager tx,
            AppProperties props, Clock clock) {
        this.logs = logs;
        this.jdbc = jdbc;
        this.newTransaction = new TransactionTemplate(tx);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.config = props.reminder();
        this.clock = clock;
    }

    /**
     * Inserts the SENDING row for this candidate. Empty when another worker already holds the same
     * (task, type, scheduledAt) key, or the task/user was deleted in the meantime (nothing left to remind).
     */
    public Optional<Long> tryClaim(ReminderCandidate candidate) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        try {
            Long id = newTransaction.execute(status -> insert(candidate, now));
            return Optional.ofNullable(id);
        } catch (DataIntegrityViolationException | ConcurrencyFailureException e) {
            // Duplicate key (another instance won), a deadlock between two inserts of the same key (the other one
            // holds it), or foreign key (task/user gone): not an error for the caller.
            LOG.debug("Reminder claim not taken: taskId={} type={}", candidate.taskId(), candidate.type());
            return Optional.empty();
        }
    }

    private Long insert(ReminderCandidate c, Instant now) {
        KeyHolder keys = new GeneratedKeyHolder();
        LocalDateTime nowUtc = utc(now);
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, c.userId());
            ps.setLong(2, c.taskId());
            ps.setString(3, c.type().name());
            ps.setObject(4, utc(c.scheduledAt().truncatedTo(ChronoUnit.MILLIS)));
            ps.setObject(5, nowUtc);
            ps.setObject(6, nowUtc);
            ps.setObject(7, nowUtc);
            return ps;
        }, keys);
        Number key = keys.getKey();
        return key == null ? null : key.longValue();
    }

    /**
     * Takes over RETRY rows that are due and SENDING rows whose worker looks dead (locked longer than the sending
     * timeout). Candidates are listed first, then each is taken with a conditional UPDATE that re-checks the
     * conditions; only rows with exactly one affected row are returned, so two workers never get the same row.
     */
    public List<Long> claimRetryable(Instant now, int limit) {
        Instant at = now.truncatedTo(ChronoUnit.MILLIS);
        Instant staleBefore = at.minus(config.sendingTimeout());
        List<Long> claimed = new ArrayList<>();
        for (Long id : logs.findClaimableIds(at, staleBefore, PageRequest.of(0, limit))) {
            if (logs.claim(id, at, staleBefore) == 1) {
                claimed.add(id);
            }
        }
        return claimed;
    }

    private static LocalDateTime utc(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
