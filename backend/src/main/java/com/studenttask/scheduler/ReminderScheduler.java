package com.studenttask.scheduler;

import com.studenttask.config.AppProperties;
import com.studenttask.notification.dto.ReminderCandidate;
import com.studenttask.notification.entity.ReminderType;
import com.studenttask.notification.repository.ReminderCandidateRepository;
import com.studenttask.notification.service.ReminderClaimService;
import com.studenttask.notification.service.ReminderDispatcher;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Finds due reminders, claims them and hands them to the dispatcher (spec section 10). Runs no database transaction
 * of its own: claiming is a short transaction inside {@link ReminderClaimService}, and sending happens in
 * {@link ReminderDispatcher} with no transaction open.
 *
 * <p>Safety: a reminder is only sent by whoever wins the unique-key insert (or the conditional retry UPDATE), so two
 * instances ticking at once cannot duplicate. The catch-up window (default 10 minutes) bounds how late anything is
 * sent, so switching the scheduler on never reminds about old tasks. At most {@code batchSize} items are handled per
 * tick, with a small pause between sends to stay under Telegram's rate limits. A tick that is still running makes the
 * next one in the same JVM return immediately.
 *
 * <p>Logging: only counts, and only when something happened; failures are logged by exception class and ids, never
 * titles, tokens or chat ids.
 */
@Component
public class ReminderScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(ReminderScheduler.class);

    private final ReminderCandidateRepository candidates;
    private final ReminderClaimService claims;
    private final ReminderDispatcher dispatcher;
    private final AppProperties.Reminder config;
    private final Clock clock;
    private final AtomicBoolean running = new AtomicBoolean();

    public ReminderScheduler(ReminderCandidateRepository candidates, ReminderClaimService claims,
            ReminderDispatcher dispatcher, AppProperties props, Clock clock) {
        this.candidates = candidates;
        this.claims = claims;
        this.dispatcher = dispatcher;
        this.config = props.reminder();
        this.clock = clock;
    }

    /**
     * The first run waits one poll interval so that application startup stays quiet. The trigger exists only while
     * {@code app.reminder.enabled} is true (see {@link SchedulingConfig}).
     */
    @Scheduled(fixedDelayString = "${app.reminder.poll-interval}", initialDelayString = "${app.reminder.poll-interval}")
    public void tick() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        try {
            runTick();
        } finally {
            running.set(false);
        }
    }

    private void runTick() {
        Instant now = clock.instant();
        Instant windowStart = now.minus(config.catchUpWindow());
        int remaining = config.batchSize();
        int sent = 0;
        for (ReminderType type : ReminderType.values()) {
            if (remaining <= 0) {
                break;
            }
            List<ReminderCandidate> due = findCandidates(type, windowStart, now, remaining);
            remaining -= due.size();
            for (ReminderCandidate candidate : due) {
                sent += claimAndDispatch(candidate) ? 1 : 0;
                if (!pause()) {
                    return;
                }
            }
        }
        int retried = 0;
        if (remaining > 0) {
            retried = retry(now, remaining);
        }
        if (sent + retried > 0) {
            LOG.info("Reminder tick: dispatched={} retried={}", sent, retried);
        }
    }

    private List<ReminderCandidate> findCandidates(ReminderType type, Instant windowStart, Instant now, int limit) {
        try {
            return candidates.findCandidates(type, windowStart, now, limit);
        } catch (RuntimeException e) {
            LOG.warn("Reminder candidate lookup failed: type={} error={}", type, e.getClass().getSimpleName());
            return List.of();
        }
    }

    private boolean claimAndDispatch(ReminderCandidate candidate) {
        try {
            Optional<Long> logId = claims.tryClaim(candidate);
            logId.ifPresent(dispatcher::dispatch);
            return logId.isPresent();
        } catch (RuntimeException e) {
            LOG.warn("Reminder failed: taskId={} type={} error={}", candidate.taskId(), candidate.type(),
                    e.getClass().getSimpleName());
            return false;
        }
    }

    private int retry(Instant now, int limit) {
        List<Long> ids;
        try {
            ids = claims.claimRetryable(now, limit);
        } catch (RuntimeException e) {
            LOG.warn("Reminder retry lookup failed: error={}", e.getClass().getSimpleName());
            return 0;
        }
        int done = 0;
        for (Long id : ids) {
            try {
                dispatcher.dispatch(id);
                done++;
            } catch (RuntimeException e) {
                LOG.warn("Reminder retry failed: logId={} error={}", id, e.getClass().getSimpleName());
            }
            if (!pause()) {
                break;
            }
        }
        return done;
    }

    /** Sleeps between two sends; false when the thread was interrupted (shutdown), then the tick stops. */
    private boolean pause() {
        Duration pause = config.sendPause();
        if (pause.isZero()) {
            return true;
        }
        try {
            Thread.sleep(pause);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
