package com.studenttask.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.studenttask.MySqlTestBase;
import com.studenttask.config.AppProperties;
import com.studenttask.notification.dto.ReminderCandidate;
import com.studenttask.notification.entity.NotificationStatus;
import com.studenttask.notification.entity.ReminderType;
import com.studenttask.notification.repository.NotificationLogRepository;
import com.studenttask.notification.service.ReminderClaimService;
import com.studenttask.notification.service.ReminderDispatcher;
import com.studenttask.notification.service.ReminderMessageFormatter;
import com.studenttask.task.repository.TaskRepository;
import com.studenttask.task.service.UserZoneResolver;
import com.studenttask.telegram.client.TelegramException;
import com.studenttask.telegram.client.TelegramException.Kind;
import com.studenttask.user.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/** Real MySQL, fake Telegram client (the real API is never called), synthetic data only. */
class ReminderDispatcherIT extends MySqlTestBase {

    private static final String TOKEN = "test-only-synthetic-bot-token";
    private static final String TITLE = "Judul Rahasia Sintetis";
    private static final Duration HOUR = Duration.ofHours(1);

    @Autowired
    NotificationLogRepository logs;
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
    ReminderClaimService claims;
    ReminderDispatcher dispatcher;

    long userId;
    long chatId;
    long taskId;
    Instant deadline;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(ReminderTestData.T0);
        data = new ReminderTestData(jdbc);
        jdbc.update("delete from notification_logs"); // rows from other tests would be claimable here
        telegram = new FakeTelegramClient();
        claims = new ReminderClaimService(logs, jdbc, tx, props, clock);
        dispatcher = new ReminderDispatcher(logs, tasks, users, zones, telegram, new ReminderMessageFormatter(), props,
                clock);
        userId = data.connectedUser();
        chatId = users.findTelegramChatIdById(userId).orElseThrow();
        deadline = ReminderTestData.T0.plus(HOUR);
        taskId = data.task(userId, TITLE, deadline, "TODO");
    }

    /** Claims the H1 reminder due right now (scheduled_at = T0 = deadline - 1h). */
    private long claim() {
        return claims.tryClaim(new ReminderCandidate(taskId, userId, ReminderType.H1, deadline.minus(HOUR)))
                .orElseThrow();
    }

    private static TelegramException timeout() {
        return new TelegramException(Kind.RETRYABLE, null, "Telegram request timed out");
    }

    @Test
    void successful_send_marks_SENT_with_sent_at_and_plain_text_to_the_users_chat() {
        long id = claim();

        dispatcher.dispatch(id);

        assertThat(telegram.sent).hasSize(1);
        assertThat(telegram.sent.get(0).chatId()).isEqualTo(chatId);
        assertThat(telegram.sent.get(0).text()).contains("Tugas: " + TITLE + "\n").contains("18:00 WIB")
                .startsWith("🔔 STUDENT TASK REMINDER");
        assertThat(data.status(id)).isEqualTo("SENT");
        assertThat(data.instantOf(id, "sent_at")).isEqualTo(ReminderTestData.T0);
    }

    @Test
    void message_contains_no_chat_id_or_token() {
        long id = claim();

        dispatcher.dispatch(id);

        String text = telegram.sent.get(0).text();
        assertThat(text).doesNotContain(String.valueOf(chatId)).doesNotContain(TOKEN).doesNotContain("http");
    }

    @Test
    void user_timezone_Asia_Makassar_sends_WITA() {
        jdbc.update("update users set timezone = 'Asia/Makassar' where id = ?", userId);
        long id = claim();

        dispatcher.dispatch(id);

        assertThat(telegram.sent.get(0).text()).contains("Waktu: 19:00 WITA");
    }

    @Test
    void sending_happens_outside_any_transaction() {
        long id = claim();
        boolean[] inTx = new boolean[1];
        telegram.duringSend = () -> inTx[0] =
                org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive();

        dispatcher.dispatch(id);

        assertThat(telegram.sent).hasSize(1);
        assertThat(inTx[0]).isFalse();
    }

    @Test
    void error_429_with_retry_after_schedules_next_retry_at_at_least_that_long() {
        telegram.failNext(new TelegramException(Kind.RETRYABLE, Duration.ofMinutes(7), "Telegram rate limited"));
        long id = claim();

        dispatcher.dispatch(id);

        assertThat(data.status(id)).isEqualTo("RETRY");
        assertThat(data.attempts(id)).isEqualTo(1);
        assertThat(data.instantOf(id, "next_retry_at")).isAfterOrEqualTo(ReminderTestData.T0.plus(Duration.ofMinutes(7)));
    }

    @Test
    void short_retry_after_never_shortens_the_normal_backoff() {
        telegram.failNext(new TelegramException(Kind.RETRYABLE, Duration.ofSeconds(1), "Telegram rate limited"));
        long id = claim();

        dispatcher.dispatch(id);

        assertThat(data.instantOf(id, "next_retry_at")).isEqualTo(ReminderTestData.T0.plus(Duration.ofMinutes(1)));
    }

    @Test
    void error_403_marks_FAILED_without_retry() {
        telegram.failNext(new TelegramException(Kind.PERMANENT, null, "Telegram rejected the request"));
        long id = claim();

        dispatcher.dispatch(id);

        assertThat(data.status(id)).isEqualTo("FAILED");
        assertThat(data.instantOf(id, "next_retry_at")).isNull();
        clock.advance(Duration.ofMinutes(30));
        assertThat(claims.claimRetryable(clock.instant(), 10)).isEmpty();
        assertThat(telegram.sent).hasSize(1);
    }

    @Test
    void timeout_backs_off_1_5_15_minutes_then_FAILED_on_4th_attempt() {
        telegram.failNext(timeout(), timeout(), timeout(), timeout());
        long id = claim();
        long[] expectedMinutes = {1, 5, 15};

        for (int attempt = 1; attempt <= 3; attempt++) {
            dispatcher.dispatch(id);
            assertThat(data.status(id)).isEqualTo("RETRY");
            assertThat(data.attempts(id)).isEqualTo(attempt);
            assertThat(data.instantOf(id, "next_retry_at"))
                    .isEqualTo(clock.instant().plus(Duration.ofMinutes(expectedMinutes[attempt - 1])));
            clock.advance(Duration.ofMinutes(expectedMinutes[attempt - 1]));
            assertThat(claims.claimRetryable(clock.instant(), 10)).containsExactly(id);
            assertThat(data.attempts(id)).isEqualTo(attempt + 1);
        }

        dispatcher.dispatch(id);

        assertThat(data.status(id)).isEqualTo("FAILED");
        assertThat(data.attempts(id)).isEqualTo(4);
        assertThat(data.instantOf(id, "next_retry_at")).isNull();
        assertThat(telegram.sent).hasSize(4);
        clock.advance(Duration.ofHours(1));
        assertThat(claims.claimRetryable(clock.instant(), 10)).isEmpty();
    }

    @Test
    void retry_that_succeeds_is_marked_SENT() {
        telegram.failNext(timeout());
        long id = claim();
        dispatcher.dispatch(id);
        clock.advance(Duration.ofMinutes(1));
        claims.claimRetryable(clock.instant(), 10);

        dispatcher.dispatch(id);

        assertThat(data.status(id)).isEqualTo("SENT");
        assertThat(data.attempts(id)).isEqualTo(2);
        assertThat(data.instantOf(id, "sent_at")).isEqualTo(clock.instant());
    }

    @Test
    void unexpected_exception_is_retried_with_a_generic_message_only() {
        telegram.failNext(new IllegalStateException("boom " + TITLE + " " + chatId));
        long id = claim();

        dispatcher.dispatch(id);

        assertThat(data.status(id)).isEqualTo("RETRY");
        String error = (String) data.row(id).get("error_message");
        assertThat(error).contains("IllegalStateException").doesNotContain(TITLE).doesNotContain(String.valueOf(chatId));
    }

    @Test
    void error_message_has_no_bot_token_or_url() {
        String leaky = "failed https://api.telegram.org/bot" + TOKEN + "/sendMessage?chat_id=" + chatId + " for " + TITLE
                + " token 123456789:AAE-abcdefghijklmnopqrstuvwxyz0123456 " + "x".repeat(900);
        telegram.failNext(new TelegramException(Kind.RETRYABLE, null, leaky));
        long id = claim();

        dispatcher.dispatch(id);

        String error = (String) data.row(id).get("error_message");
        assertThat(error).isNotBlank().hasSizeLessThanOrEqualTo(500).doesNotContain(TOKEN).doesNotContain("http")
                .doesNotContain("api.telegram.org").doesNotContain(String.valueOf(chatId)).doesNotContain(TITLE)
                .doesNotContain("AAE-");
    }

    @Test
    void task_completed_between_claim_and_send_is_SKIPPED_and_nothing_sent() {
        long id = claim();
        jdbc.update("update tasks set status = 'COMPLETED' where id = ?", taskId);

        dispatcher.dispatch(id);

        assertThat(data.status(id)).isEqualTo("SKIPPED");
        assertThat(telegram.sent).isEmpty();
    }

    @Test
    void telegram_disconnected_before_send_is_SKIPPED() {
        long id = claim();
        jdbc.update("update users set telegram_chat_id = null where id = ?", userId);

        dispatcher.dispatch(id);

        assertThat(data.status(id)).isEqualTo("SKIPPED");
        assertThat(telegram.sent).isEmpty();
    }

    @Test
    void first_attempt_beyond_catch_up_window_is_SKIPPED() {
        long id = claim();
        clock.advance(Duration.ofMinutes(10).plusMillis(1));

        dispatcher.dispatch(id);

        assertThat(data.status(id)).isEqualTo("SKIPPED");
        assertThat(telegram.sent).isEmpty();
    }

    @Test
    void retry_after_deadline_plus_catch_up_window_is_SKIPPED_but_inside_it_is_sent() {
        long late = data.log(userId, taskId, ReminderType.H24, deadline.minus(Duration.ofHours(24)),
                NotificationStatus.SENDING, 2, null, ReminderTestData.T0);
        clock.set(deadline.plus(Duration.ofMinutes(10)));
        jdbc.update("update notification_logs set locked_at = ? where id = ?", ReminderTestData.utc(clock.instant()), late);

        dispatcher.dispatch(late);
        assertThat(data.status(late)).as("exactly deadline + window is still inside").isEqualTo("SENT");

        long tooLate = data.log(userId, taskId, ReminderType.H6, deadline.minus(Duration.ofHours(6)),
                NotificationStatus.SENDING, 2, null, clock.instant().plusMillis(1));
        clock.advance(Duration.ofMillis(2));
        dispatcher.dispatch(tooLate);
        assertThat(data.status(tooLate)).isEqualTo("SKIPPED");
        assertThat(telegram.sent).hasSize(1);
    }

    @Test
    void retry_row_for_an_old_schedule_is_SKIPPED_after_the_deadline_was_moved() {
        long id = claim();
        jdbc.update("update tasks set deadline = ? where id = ?", ReminderTestData.utc(deadline.plus(Duration.ofDays(1))),
                taskId);

        dispatcher.dispatch(id);

        assertThat(data.status(id)).isEqualTo("SKIPPED");
        assertThat(telegram.sent).isEmpty();
    }

    @Test
    void row_that_already_used_all_attempts_is_FAILED_without_sending() {
        long id = data.log(userId, taskId, ReminderType.H1, deadline.minus(HOUR), NotificationStatus.SENDING, 5, null,
                ReminderTestData.T0);

        dispatcher.dispatch(id);

        assertThat(data.status(id)).isEqualTo("FAILED");
        assertThat(telegram.sent).isEmpty();
    }

    @Test
    void non_SENDING_rows_and_unknown_ids_are_ignored() {
        long sent = data.log(userId, taskId, ReminderType.H1, deadline.minus(HOUR), NotificationStatus.SENT, 1, null,
                ReminderTestData.T0);
        long retry = data.log(userId, taskId, ReminderType.H6, deadline.minus(HOUR), NotificationStatus.RETRY, 1,
                ReminderTestData.T0, ReminderTestData.T0);

        dispatcher.dispatch(sent);
        dispatcher.dispatch(retry);
        dispatcher.dispatch(987_654_321L);

        assertThat(telegram.sent).isEmpty();
        assertThat(data.status(sent)).isEqualTo("SENT");
        assertThat(data.status(retry)).isEqualTo("RETRY");
    }

    @Test
    void sent_exactly_once_even_if_dispatch_is_called_twice_concurrently_for_the_same_id() throws Exception {
        long id = claim();
        CountDownLatch inSend = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        telegram.duringSend = () -> {
            inSend.countDown();
            await(release);
        };

        CompletableFuture<Void> first = CompletableFuture.runAsync(() -> dispatcher.dispatch(id));
        assertThat(inSend.await(10, TimeUnit.SECONDS)).isTrue();
        CompletableFuture<Void> second = CompletableFuture.runAsync(() -> dispatcher.dispatch(id));
        second.get(10, TimeUnit.SECONDS);
        release.countDown();
        first.get(10, TimeUnit.SECONDS);
        dispatcher.dispatch(id);

        assertThat(telegram.sent).hasSize(1);
        assertThat(data.status(id)).isEqualTo("SENT");
    }

    @Test
    void a_stale_workers_late_success_does_not_overwrite_a_newer_owner() throws Exception {
        long id = claim();
        CountDownLatch inSend = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        telegram.duringSend = () -> {
            inSend.countDown();
            await(release);
        };
        CompletableFuture<Void> stale = CompletableFuture.runAsync(() -> dispatcher.dispatch(id));
        assertThat(inSend.await(10, TimeUnit.SECONDS)).isTrue();

        clock.advance(Duration.ofMinutes(5).plusMillis(1)); // worker looks dead; another worker takes the row over
        assertThat(claims.claimRetryable(clock.instant(), 10)).containsExactly(id);
        Instant newOwnerLock = data.instantOf(id, "locked_at");
        release.countDown();
        stale.get(10, TimeUnit.SECONDS);

        assertThat(data.status(id)).isEqualTo("SENDING");
        assertThat(data.attempts(id)).isEqualTo(2);
        assertThat(data.instantOf(id, "locked_at")).isEqualTo(newOwnerLock);
        assertThat(data.instantOf(id, "sent_at")).isNull();
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
