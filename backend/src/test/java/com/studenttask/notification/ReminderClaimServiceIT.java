package com.studenttask.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.studenttask.MySqlTestBase;
import com.studenttask.config.AppProperties;
import com.studenttask.notification.dto.ReminderCandidate;
import com.studenttask.notification.entity.NotificationStatus;
import com.studenttask.notification.entity.ReminderType;
import com.studenttask.notification.repository.NotificationLogRepository;
import com.studenttask.notification.service.ReminderClaimService;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Real MySQL, real threads, synthetic data only. */
class ReminderClaimServiceIT extends MySqlTestBase {

    @Autowired
    NotificationLogRepository logs;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    PlatformTransactionManager tx;
    @Autowired
    AppProperties props;

    MutableClock clock;
    ReminderTestData data;
    ReminderClaimService claims;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(ReminderTestData.T0);
        data = new ReminderTestData(jdbc);
        jdbc.update("delete from notification_logs"); // rows from other tests would be claimable here
        claims = new ReminderClaimService(logs, jdbc, tx, props, clock);
    }

    private record Seed(long userId, long taskId) {
    }

    private Seed seed() {
        long user = data.connectedUser();
        long task = data.task(user, "Tugas Sintetis", ReminderTestData.T0.plus(Duration.ofHours(1)), "TODO");
        return new Seed(user, task);
    }

    private ReminderCandidate candidate(Seed s) {
        return new ReminderCandidate(s.taskId(), s.userId(), ReminderType.H1, ReminderTestData.T0);
    }

    @Test
    void try_claim_inserts_SENDING_row_with_attempts_1_and_locked_at_now() {
        Seed s = seed();

        Optional<Long> id = claims.tryClaim(candidate(s));

        assertThat(id).isPresent();
        assertThat(data.status(id.get())).isEqualTo("SENDING");
        assertThat(data.attempts(id.get())).isEqualTo(1);
        assertThat(data.instantOf(id.get(), "locked_at")).isEqualTo(ReminderTestData.T0);
        assertThat(data.instantOf(id.get(), "scheduled_at")).isEqualTo(ReminderTestData.T0);
        assertThat(data.row(id.get()).get("reminder_type")).isEqualTo("H1");
    }

    @Test
    void second_claim_of_same_key_returns_empty_and_keeps_one_row() {
        Seed s = seed();

        assertThat(claims.tryClaim(candidate(s))).isPresent();
        assertThat(claims.tryClaim(candidate(s))).isEmpty();

        assertThat(data.countLogs(s.taskId())).isEqualTo(1);
    }

    @Test
    void duplicate_claim_inside_callers_transaction_does_not_poison_it() {
        Seed s = seed();
        claims.tryClaim(candidate(s));
        TransactionTemplate caller = new TransactionTemplate(tx);

        Integer result = caller.execute(status -> {
            jdbc.update("update tasks set title = 'Diubah' where id = ?", s.taskId());
            assertThat(claims.tryClaim(candidate(s))).isEmpty();
            return jdbc.queryForObject("select count(*) from tasks where title = 'Diubah'", Integer.class);
        });

        assertThat(result).isEqualTo(1);
        assertThat(jdbc.queryForObject("select title from tasks where id = ?", String.class, s.taskId()))
                .isEqualTo("Diubah");
    }

    @Test
    void claim_for_a_task_that_no_longer_exists_returns_empty() {
        Seed s = seed();
        jdbc.update("delete from tasks where id = ?", s.taskId());

        assertThat(claims.tryClaim(candidate(s))).isEmpty();
    }

    @Test
    void concurrent_claims_by_16_threads_yield_exactly_one_winner() throws Exception {
        Seed s = seed();
        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<Optional<Long>>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return claims.tryClaim(candidate(s));
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            int winners = 0;
            for (Future<Optional<Long>> f : futures) {
                if (f.get(30, TimeUnit.SECONDS).isPresent()) {
                    winners++;
                }
            }
            assertThat(winners).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
        assertThat(data.countLogs(s.taskId())).isEqualTo(1);
    }

    @Test
    void claim_retryable_by_two_workers_returns_row_to_only_one() throws Exception {
        Seed s = seed();
        long id = data.log(s.userId(), s.taskId(), ReminderType.H1, ReminderTestData.T0, NotificationStatus.RETRY, 1,
                ReminderTestData.T0.minusSeconds(1), ReminderTestData.T0.minusSeconds(120));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<List<Long>>> futures = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                futures.add(pool.submit(() -> {
                    go.await();
                    return claims.claimRetryable(clock.instant(), 10);
                }));
            }
            go.countDown();
            List<Long> all = new ArrayList<>();
            for (Future<List<Long>> f : futures) {
                all.addAll(f.get(30, TimeUnit.SECONDS));
            }
            assertThat(all).containsExactly(id);
        } finally {
            pool.shutdownNow();
        }
        assertThat(data.status(id)).isEqualTo("SENDING");
        assertThat(data.attempts(id)).isEqualTo(2);
        assertThat(data.instantOf(id, "locked_at")).isEqualTo(ReminderTestData.T0);
        assertThat(data.instantOf(id, "next_retry_at")).isNull();
    }

    @Test
    void retry_row_is_not_picked_before_next_retry_at() {
        Seed s = seed();
        long id = data.log(s.userId(), s.taskId(), ReminderType.H1, ReminderTestData.T0, NotificationStatus.RETRY, 1,
                ReminderTestData.T0.plusSeconds(60), ReminderTestData.T0);

        assertThat(claims.claimRetryable(ReminderTestData.T0.plusSeconds(59), 10)).isEmpty();
        assertThat(claims.claimRetryable(ReminderTestData.T0.plusSeconds(60), 10)).containsExactly(id);
    }

    @Test
    void stale_SENDING_older_than_5_minutes_is_reclaimed_once() {
        Seed s = seed();
        long id = data.log(s.userId(), s.taskId(), ReminderType.H1, ReminderTestData.T0, NotificationStatus.SENDING, 1,
                null, ReminderTestData.T0);

        assertThat(claims.claimRetryable(ReminderTestData.T0.plus(Duration.ofMinutes(5)), 10))
                .as("exactly 5 minutes is not older than 5 minutes").isEmpty();
        Instant later = ReminderTestData.T0.plus(Duration.ofMinutes(5)).plusMillis(1);
        assertThat(claims.claimRetryable(later, 10)).containsExactly(id);
        assertThat(claims.claimRetryable(later, 10)).isEmpty();
        assertThat(claims.claimRetryable(later.plusSeconds(30), 10)).isEmpty();

        assertThat(data.attempts(id)).isEqualTo(2);
        assertThat(data.instantOf(id, "locked_at")).isEqualTo(later);
    }

    @Test
    void fresh_SENDING_row_is_not_reclaimed() {
        Seed s = seed();
        data.log(s.userId(), s.taskId(), ReminderType.H1, ReminderTestData.T0, NotificationStatus.SENDING, 1, null,
                ReminderTestData.T0);

        assertThat(claims.claimRetryable(ReminderTestData.T0.plus(Duration.ofMinutes(4)), 10)).isEmpty();
    }

    @Test
    void FAILED_SKIPPED_and_SENT_rows_are_never_reclaimed() {
        Seed s = seed();
        Instant old = ReminderTestData.T0.minus(Duration.ofDays(1));
        for (NotificationStatus status : List.of(NotificationStatus.FAILED, NotificationStatus.SKIPPED,
                NotificationStatus.SENT)) {
            ReminderType type = ReminderType.values()[status.ordinal() % 4];
            data.log(s.userId(), s.taskId(), type, ReminderTestData.T0.minusSeconds(status.ordinal()), status, 4,
                    old, old);
        }

        assertThat(claims.claimRetryable(ReminderTestData.T0.plus(Duration.ofDays(2)), 10)).isEmpty();
    }

    @Test
    void claim_retryable_respects_limit_and_leaves_the_rest_for_the_next_call() {
        Seed s = seed();
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            ids.add(data.log(s.userId(), s.taskId(), ReminderType.H1, ReminderTestData.T0.minusSeconds(i + 1),
                    NotificationStatus.RETRY, 1, ReminderTestData.T0.minusSeconds(1), ReminderTestData.T0));
        }

        assertThat(claims.claimRetryable(ReminderTestData.T0, 2)).hasSize(2);
        assertThat(claims.claimRetryable(ReminderTestData.T0, 2)).hasSize(1);
    }

    @Test
    void deleted_task_cascade_removes_pending_retry() {
        Seed s = seed();
        long id = data.log(s.userId(), s.taskId(), ReminderType.H1, ReminderTestData.T0, NotificationStatus.RETRY, 1,
                ReminderTestData.T0.plusSeconds(60), ReminderTestData.T0);

        jdbc.update("delete from tasks where id = ?", s.taskId());

        assertThat(logs.findById(id)).isEmpty();
        assertThat(claims.claimRetryable(ReminderTestData.T0.plus(Duration.ofHours(1)), 10)).doesNotContain(id);
    }
}
