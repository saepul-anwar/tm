package com.studenttask.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;

import com.studenttask.MySqlTestBase;
import com.studenttask.config.AppProperties;
import com.studenttask.notification.entity.ReminderType;
import com.studenttask.notification.repository.NotificationLogRepository;
import com.studenttask.notification.repository.ReminderCandidateRepository;
import com.studenttask.notification.service.ReminderClaimService;
import com.studenttask.notification.service.ReminderDispatcher;
import com.studenttask.notification.service.ReminderMessageFormatter;
import com.studenttask.scheduler.ReminderScheduler;
import com.studenttask.task.repository.TaskRepository;
import com.studenttask.task.service.UserZoneResolver;
import com.studenttask.telegram.client.TelegramException;
import com.studenttask.telegram.client.TelegramException.Kind;
import com.studenttask.user.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/** Real MySQL, injected clock, fake Telegram (the real API is never called), synthetic data only. */
@ExtendWith(OutputCaptureExtension.class)
class ReminderSchedulerIT extends MySqlTestBase {

    private static final Instant T0 = ReminderTestData.T0;
    private static final String TITLE = "Judul Rahasia Sintetis";

    @Autowired
    NotificationLogRepository logs;
    @Autowired
    ReminderCandidateRepository candidates;
    @Autowired
    TaskRepository tasks;
    @Autowired
    UserRepository users;
    @Autowired
    UserZoneResolver zones;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    PlatformTransactionManager tx;
    @Autowired
    AppProperties props;

    MutableClock clock;
    ReminderTestData data;
    FakeTelegramClient telegram;
    ReminderScheduler scheduler;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(T0);
        data = new ReminderTestData(jdbc);
        data.deleteAll(); // rows left by other test classes would be picked up by the global candidate query
        telegram = new FakeTelegramClient();
        scheduler = newScheduler(null);
    }

    @AfterEach
    void tearDown() {
        data.deleteAll();
    }

    private ReminderScheduler newScheduler(java.util.function.UnaryOperator<ReminderDispatcher> wrap) {
        ReminderClaimService claims = new ReminderClaimService(logs, jdbc, tx, props, clock);
        ReminderDispatcher dispatcher = new ReminderDispatcher(logs, tasks, users, zones, telegram,
                new ReminderMessageFormatter(), props, clock);
        return new ReminderScheduler(candidates, claims, wrap == null ? dispatcher : wrap.apply(dispatcher), props,
                clock);
    }

    private void tickAt(Instant instant) {
        clock.set(instant);
        scheduler.tick();
    }

    private int sentCount(long taskId, ReminderType type) {
        return jdbc.queryForObject("select count(*) from notification_logs where task_id = ? and reminder_type = ?"
                + " and status = 'SENT'", Integer.class, taskId, type.name());
    }

    @Test
    void sends_H24_H6_H1_and_AT_DEADLINE_at_their_times_once_each() {
        long user = data.connectedUser();
        Instant deadline = T0.plus(Duration.ofHours(25));
        long task = data.task(user, TITLE, deadline, "TODO");

        tickAt(T0); // nothing is due yet
        assertThat(telegram.sent).isEmpty();

        tickAt(deadline.minus(Duration.ofHours(24)).plusSeconds(60));
        tickAt(deadline.minus(Duration.ofHours(24)).plusSeconds(120)); // repeated tick: still once
        assertThat(sentCount(task, ReminderType.H24)).isEqualTo(1);
        assertThat(telegram.sent).hasSize(1);

        tickAt(deadline.minus(Duration.ofHours(6)).plusSeconds(60));
        assertThat(sentCount(task, ReminderType.H6)).isEqualTo(1);
        tickAt(deadline.minus(Duration.ofHours(1)).plusSeconds(60));
        assertThat(sentCount(task, ReminderType.H1)).isEqualTo(1);
        tickAt(deadline.plusSeconds(60));
        assertThat(sentCount(task, ReminderType.AT_DEADLINE)).isEqualTo(1);
        tickAt(deadline.plusSeconds(120));

        assertThat(telegram.sent).hasSize(4);
        assertThat(data.countLogs(task)).isEqualTo(4);
        assertThat(telegram.sent).allSatisfy(s -> assertThat(s.text()).contains(TITLE));
    }

    @Test
    void completing_the_task_in_between_stops_the_later_reminders() {
        long user = data.connectedUser();
        Instant deadline = T0.plus(Duration.ofHours(25));
        long task = data.task(user, TITLE, deadline, "TODO");

        tickAt(deadline.minus(Duration.ofHours(24)).plusSeconds(60));
        data.setTaskStatus(task, "COMPLETED");
        tickAt(deadline.minus(Duration.ofHours(6)).plusSeconds(60));
        tickAt(deadline.minus(Duration.ofHours(1)).plusSeconds(60));
        tickAt(deadline.plusSeconds(60));

        assertThat(telegram.sent).hasSize(1);
        assertThat(data.countLogs(task)).isEqualTo(1);
    }

    @ParameterizedTest
    @EnumSource(ReminderType.class)
    void disabled_toggle_sends_nothing_for_that_type(ReminderType type) {
        long user = data.connectedUser();
        data.settings(user, type != ReminderType.H24, type != ReminderType.H6, type != ReminderType.H1,
                type != ReminderType.AT_DEADLINE);
        Instant deadline = T0.plus(Duration.ofHours(30));
        long task = data.task(user, TITLE, deadline, "TODO");

        tickAt(deadline.minus(type.offset()).plusSeconds(60));

        assertThat(telegram.sent).isEmpty();
        assertThat(data.countLogs(task)).isZero();
    }

    @Test
    void enabled_toggles_still_send_when_other_types_are_off() {
        long user = data.connectedUser();
        data.settings(user, false, false, true, false);
        Instant deadline = T0.plus(Duration.ofHours(30));
        data.task(user, TITLE, deadline, "TODO");

        tickAt(deadline.minus(Duration.ofHours(1)).plusSeconds(60));

        assertThat(telegram.sent).hasSize(1);
    }

    @Test
    void user_without_a_settings_row_gets_the_defaults_all_on() {
        long user = data.connectedUser(); // no reminder_settings row
        Instant deadline = T0.plus(Duration.ofHours(30));
        long task = data.task(user, TITLE, deadline, "TODO");

        tickAt(deadline.minus(Duration.ofHours(6)).plusSeconds(60));

        assertThat(sentCount(task, ReminderType.H6)).isEqualTo(1);
    }

    @Test
    void completed_task_never_selected() {
        long user = data.connectedUser();
        long task = data.task(user, TITLE, T0.plus(Duration.ofHours(1)), "COMPLETED");

        tickAt(T0.plusSeconds(60));

        assertThat(telegram.sent).isEmpty();
        assertThat(data.countLogs(task)).isZero();
    }

    @Test
    void user_without_telegram_never_selected() {
        long user = data.user(null, "Asia/Jakarta");
        long task = data.task(user, TITLE, T0.plus(Duration.ofHours(1)), "TODO");

        tickAt(T0.plusSeconds(60));

        assertThat(telegram.sent).isEmpty();
        assertThat(data.countLogs(task)).isZero();
    }

    @Test
    void task_deleted_before_tick_sends_nothing() {
        long user = data.connectedUser();
        long task = data.task(user, TITLE, T0.plus(Duration.ofHours(1)), "TODO");
        data.deleteTask(task);

        tickAt(T0.plusSeconds(60));

        assertThat(telegram.sent).isEmpty();
        assertThat(data.countLogs()).isZero();
    }

    @Test
    void task_created_with_deadline_in_3h_skips_H24_and_H6_sends_H1() {
        long user = data.connectedUser();
        long task = data.task(user, TITLE, T0.plus(Duration.ofHours(3)), "TODO");

        tickAt(T0.plusSeconds(60));
        assertThat(telegram.sent).isEmpty(); // H24 and H6 are long past: never sent late

        tickAt(T0.plus(Duration.ofHours(2)).plusSeconds(60));

        assertThat(sentCount(task, ReminderType.H1)).isEqualTo(1);
        assertThat(telegram.sent).hasSize(1);
        assertThat(data.countLogs(task)).isEqualTo(1);
    }

    @Test
    void scheduled_time_older_than_10_minutes_is_not_sent_late() {
        long user = data.connectedUser();
        data.task(user, TITLE, T0.plus(Duration.ofHours(1)), "TODO"); // H1 scheduled at T0

        tickAt(T0.plus(Duration.ofMinutes(11)));

        assertThat(telegram.sent).isEmpty();
        assertThat(data.countLogs()).isZero();
    }

    @Test
    void reminder_missed_by_a_short_outage_is_still_sent_within_the_window() {
        long user = data.connectedUser();
        data.task(user, TITLE, T0.plus(Duration.ofHours(1)), "TODO"); // H1 scheduled at T0

        tickAt(T0.plus(Duration.ofMinutes(9)));

        assertThat(telegram.sent).hasSize(1);
    }

    @Test
    void deadline_moved_later_after_H1_sent_creates_new_reminder_for_new_schedule() {
        long user = data.connectedUser();
        long task = data.task(user, TITLE, T0.plus(Duration.ofHours(1)), "TODO");
        tickAt(T0.plusSeconds(60));
        assertThat(sentCount(task, ReminderType.H1)).isEqualTo(1);

        Instant newDeadline = T0.plus(Duration.ofHours(3));
        data.setDeadline(task, newDeadline);
        tickAt(T0.plus(Duration.ofMinutes(30))); // old key already used, new key not yet due
        assertThat(telegram.sent).hasSize(1);
        tickAt(newDeadline.minus(Duration.ofHours(1)).plusSeconds(60));

        assertThat(telegram.sent).hasSize(2);
        assertThat(sentCount(task, ReminderType.H1)).isEqualTo(2);
        assertThat(data.countLogs(task)).isEqualTo(2);
    }

    @Test
    void reopened_completed_task_sends_only_within_window() {
        long user = data.connectedUser();
        Instant deadline = T0.plus(Duration.ofHours(1)); // H1 scheduled at T0
        long reopenedEarly = data.task(user, TITLE, deadline, "COMPLETED");
        long reopenedLate = data.task(user, TITLE, deadline, "COMPLETED");

        data.setTaskStatus(reopenedEarly, "TODO");
        tickAt(T0.plus(Duration.ofMinutes(5)));
        assertThat(sentCount(reopenedEarly, ReminderType.H1)).isEqualTo(1);

        data.setTaskStatus(reopenedLate, "IN_PROGRESS");
        tickAt(T0.plus(Duration.ofMinutes(15)));

        assertThat(data.countLogs(reopenedLate)).isZero();
        assertThat(telegram.sent).hasSize(1);
    }

    @Test
    void two_ticks_in_a_row_send_once() {
        long user = data.connectedUser();
        long task = data.task(user, TITLE, T0.plus(Duration.ofHours(1)), "TODO");

        tickAt(T0.plusSeconds(60));
        scheduler.tick();

        assertThat(telegram.sent).hasSize(1);
        assertThat(data.countLogs(task)).isEqualTo(1);
    }

    @Test
    void two_scheduler_instances_ticking_concurrently_send_once() throws Exception {
        long user = data.connectedUser();
        data.tasks(user, 40, T0.plus(Duration.ofHours(1)));
        clock.set(T0.plusSeconds(60));
        ReminderScheduler other = newScheduler(null);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch go = new CountDownLatch(1);
            List<Future<?>> runs = List.of(
                    pool.submit(() -> { go.await(); scheduler.tick(); return null; }),
                    pool.submit(() -> { go.await(); other.tick(); return null; }));
            go.countDown();
            for (Future<?> run : runs) {
                run.get(60, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(telegram.sent).hasSize(40);
        assertThat(data.countLogs()).isEqualTo(40);
        assertThat(data.countSent()).isEqualTo(40);
    }

    @Test
    void overlapping_ticks_on_one_instance_do_not_run_in_parallel() throws Exception {
        long user = data.connectedUser();
        data.tasks(user, 2, T0.plus(Duration.ofHours(1)));
        clock.set(T0.plusSeconds(60));
        CountDownLatch inSend = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        telegram.duringSend = () -> {
            inSend.countDown();
            try {
                release.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<?> first = pool.submit(() -> scheduler.tick());
            assertThat(inSend.await(30, TimeUnit.SECONDS)).isTrue();

            scheduler.tick(); // returns immediately: a tick is already running in this JVM

            assertThat(telegram.sent).hasSize(1);
            release.countDown();
            first.get(30, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
        assertThat(telegram.sent).hasSize(2);
    }

    @Test
    void batch_limit_200_per_tick_respected() {
        long user = data.connectedUser();
        data.tasks(user, 230, T0.plus(Duration.ofHours(1)));
        clock.set(T0.plusSeconds(60));

        scheduler.tick();
        assertThat(telegram.sent).hasSize(200);

        scheduler.tick();
        assertThat(telegram.sent).hasSize(230);
        assertThat(data.countLogs()).isEqualTo(230);
    }

    @Test
    void changing_user_timezone_does_not_change_schedule() {
        long user = data.connectedUser();
        long task = data.task(user, TITLE, T0.plus(Duration.ofHours(1)), "TODO"); // H1 at T0
        data.setTimezone(user, "America/New_York");

        tickAt(T0.minusSeconds(60));
        assertThat(telegram.sent).isEmpty();
        tickAt(T0.plusSeconds(60));

        assertThat(telegram.sent).hasSize(1);
        long logId = jdbc.queryForObject("select id from notification_logs where task_id = ?", Long.class, task);
        assertThat(data.instantOf(logId, "scheduled_at")).isEqualTo(T0);
    }

    @Test
    void a_failing_telegram_send_does_not_stop_the_others_and_the_failed_row_is_retried_later() {
        long user = data.connectedUser();
        data.tasks(user, 3, T0.plus(Duration.ofHours(1)));
        telegram.failNext(new TelegramException(Kind.RETRYABLE, null, "Telegram request timed out"));
        Instant firstTick = T0.plusSeconds(60);

        tickAt(firstTick);

        assertThat(telegram.sent).hasSize(3); // first attempt failed, the other two went out
        assertThat(data.countSent()).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from notification_logs where status = 'RETRY'",
                Integer.class)).isEqualTo(1);

        tickAt(firstTick.plusSeconds(30)); // backoff (1 minute) not over yet
        assertThat(telegram.sent).hasSize(3);

        tickAt(firstTick.plus(Duration.ofMinutes(1)).plusSeconds(1));

        assertThat(telegram.sent).hasSize(4);
        assertThat(data.countSent()).isEqualTo(3);
        assertThat(jdbc.queryForObject("select max(attempts) from notification_logs", Integer.class)).isEqualTo(2);
    }

    @Test
    void an_unexpected_exception_for_one_candidate_does_not_abort_the_rest_and_is_logged_without_user_data(
            CapturedOutput output) {
        long user = data.connectedUser();
        data.tasks(user, 3, T0.plus(Duration.ofHours(1)));
        clock.set(T0.plusSeconds(60));
        scheduler = newScheduler(real -> {
            ReminderDispatcher spied = spy(real);
            doThrow(new IllegalStateException("boom " + TITLE + " secret-chat-id-123456"))
                    .doCallRealMethod().when(spied).dispatch(anyLong());
            return spied;
        });

        scheduler.tick();

        assertThat(telegram.sent).hasSize(2);
        assertThat(output.getAll()).contains("IllegalStateException").doesNotContain(TITLE)
                .doesNotContain("secret-chat-id-123456");
    }

    @Test
    void finished_rows_are_never_picked_again() {
        long user = data.connectedUser();
        long task = data.task(user, TITLE, T0.plus(Duration.ofHours(1)), "TODO");
        tickAt(T0.plusSeconds(60));

        for (int i = 2; i < 6; i++) {
            tickAt(T0.plusSeconds(60L * i)); // SENT row, same key: nothing again
        }

        assertThat(telegram.sent).hasSize(1);
        assertThat(data.countLogs(task)).isEqualTo(1);
    }

    @Test
    void a_quiet_tick_writes_no_log_output(CapturedOutput output) {
        data.connectedUser();
        int before = output.getAll().length();

        tickAt(T0);

        assertThat(output.getAll().substring(before)).isEmpty();
    }
}
