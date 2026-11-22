package com.studenttask.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.studenttask.MySqlTestBase;
import com.studenttask.notification.dto.ReminderCandidate;
import com.studenttask.notification.entity.NotificationStatus;
import com.studenttask.notification.entity.ReminderType;
import com.studenttask.notification.repository.ReminderCandidateRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** The candidate query (spec section 10, step 1), one case per exclusion rule. Synthetic data only. */
class ReminderCandidateRepositoryIT extends MySqlTestBase {

    private static final Instant NOW = ReminderTestData.T0;
    private static final Duration WINDOW = Duration.ofMinutes(10);

    @Autowired
    ReminderCandidateRepository repository;
    @Autowired
    JdbcTemplate jdbc;

    ReminderTestData data;
    long user;

    @BeforeEach
    void setUp() {
        data = new ReminderTestData(jdbc);
        data.deleteAll();
        user = data.connectedUser();
    }

    @AfterEach
    void tearDown() {
        data.deleteAll();
    }

    private List<ReminderCandidate> find(ReminderType type) {
        return repository.findCandidates(type, NOW.minus(WINDOW), NOW, 200);
    }

    /** Deadline such that the reminder of this type is scheduled {@code age} before NOW. */
    private static Instant deadlineFor(ReminderType type, Duration age) {
        return NOW.minus(age).plus(type.offset());
    }

    @ParameterizedTest
    @EnumSource(ReminderType.class)
    void returns_a_due_task_with_task_user_type_and_scheduled_time(ReminderType type) {
        long task = data.task(user, "Tugas", deadlineFor(type, Duration.ofMinutes(3)), "TODO");

        assertThat(find(type)).containsExactly(
                new ReminderCandidate(task, user, type, NOW.minus(Duration.ofMinutes(3))));
        for (ReminderType other : ReminderType.values()) {
            if (other != type) {
                assertThat(find(other)).as("only its own type's window").isEmpty();
            }
        }
    }

    @Test
    void window_start_is_exclusive_and_now_is_inclusive() {
        long atNow = data.task(user, "Sekarang", deadlineFor(ReminderType.H1, Duration.ZERO), "TODO");
        data.task(user, "Tepat batas", deadlineFor(ReminderType.H1, WINDOW), "TODO");
        data.task(user, "Terlalu lama", deadlineFor(ReminderType.H1, WINDOW.plusMillis(1)), "TODO");
        data.task(user, "Belum waktunya", deadlineFor(ReminderType.H1, Duration.ofMillis(-1)), "TODO");
        long justInside = data.task(user, "Hampir", deadlineFor(ReminderType.H1, WINDOW.minusMillis(1)), "TODO");

        assertThat(find(ReminderType.H1)).extracting(ReminderCandidate::taskId)
                .containsExactly(justInside, atNow);
    }

    @Test
    void completed_task_is_excluded_but_todo_and_in_progress_are_included() {
        Instant deadline = deadlineFor(ReminderType.H6, Duration.ofMinutes(1));
        data.task(user, "Selesai", deadline, "COMPLETED");
        long todo = data.task(user, "Todo", deadline, "TODO");
        long doing = data.task(user, "Jalan", deadline, "IN_PROGRESS");

        assertThat(find(ReminderType.H6)).extracting(ReminderCandidate::taskId).containsExactly(todo, doing);
    }

    @Test
    void owner_without_telegram_is_excluded() {
        long unlinked = data.user(null, "Asia/Jakarta");
        data.task(unlinked, "Tanpa telegram", deadlineFor(ReminderType.H1, Duration.ofMinutes(1)), "TODO");

        assertThat(find(ReminderType.H1)).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(ReminderType.class)
    void owner_with_that_toggle_off_is_excluded_only_for_that_type(ReminderType off) {
        data.settings(user, off != ReminderType.H24, off != ReminderType.H6, off != ReminderType.H1,
                off != ReminderType.AT_DEADLINE);
        for (ReminderType type : ReminderType.values()) {
            data.task(user, "Tugas " + type, deadlineFor(type, Duration.ofMinutes(1)), "TODO");
            assertThat(find(type).isEmpty()).as(type.name()).isEqualTo(type == off);
        }
    }

    @Test
    void owner_without_settings_row_is_treated_as_all_toggles_on() {
        for (ReminderType type : ReminderType.values()) {
            data.task(user, "Tugas " + type, deadlineFor(type, Duration.ofMinutes(1)), "TODO");
            assertThat(find(type)).hasSize(1);
        }
    }

    @ParameterizedTest
    @EnumSource(NotificationStatus.class)
    void a_log_row_for_the_same_key_excludes_the_candidate_whatever_its_status(NotificationStatus status) {
        long task = data.task(user, "Tugas", deadlineFor(ReminderType.H1, Duration.ofMinutes(2)), "TODO");
        Instant scheduled = NOW.minus(Duration.ofMinutes(2));
        data.log(user, task, ReminderType.H1, scheduled, status, 1, null, null);

        assertThat(find(ReminderType.H1)).isEmpty();
    }

    @Test
    void a_log_row_for_another_type_or_another_schedule_does_not_exclude() {
        long task = data.task(user, "Tugas", deadlineFor(ReminderType.H1, Duration.ofMinutes(2)), "TODO");
        Instant scheduled = NOW.minus(Duration.ofMinutes(2));
        data.log(user, task, ReminderType.H6, scheduled, NotificationStatus.SENT, 1, null, null);
        data.log(user, task, ReminderType.H1, scheduled.minus(Duration.ofHours(2)), NotificationStatus.SENT, 1, null,
                null);

        assertThat(find(ReminderType.H1)).extracting(ReminderCandidate::taskId).containsExactly(task);
    }

    @Test
    void results_are_ordered_by_scheduled_time_then_task_id_and_limited() {
        long late = data.task(user, "Terakhir", deadlineFor(ReminderType.H1, Duration.ofMinutes(1)), "TODO");
        long earlyA = data.task(user, "A", deadlineFor(ReminderType.H1, Duration.ofMinutes(5)), "TODO");
        long earlyB = data.task(user, "B", deadlineFor(ReminderType.H1, Duration.ofMinutes(5)), "TODO");

        assertThat(find(ReminderType.H1)).extracting(ReminderCandidate::taskId).containsExactly(earlyA, earlyB, late);
        assertThat(repository.findCandidates(ReminderType.H1, NOW.minus(WINDOW), NOW, 2))
                .extracting(ReminderCandidate::taskId).containsExactly(earlyA, earlyB);
    }

    @Test
    void sub_second_deadlines_keep_their_exact_scheduled_time() {
        Instant deadline = NOW.plus(Duration.ofHours(1)).minusMillis(250);
        data.task(user, "Milidetik", deadline, "TODO");

        assertThat(find(ReminderType.H1)).extracting(ReminderCandidate::scheduledAt)
                .containsExactly(NOW.minusMillis(250));
    }

    /** Large enough that the optimizer's choice is meaningful; the output is recorded in docs/DATABASE_DESIGN.md. */
    @Test
    void explain_uses_a_range_index_on_tasks_and_not_a_full_scan() {
        for (int i = 0; i < 6; i++) {
            long u = data.connectedUser();
            data.spreadTasks(u, 1000, NOW.minus(Duration.ofHours(400)), Duration.ofHours(1));
        }
        // A realistic log table: with an empty one the optimizer may materialize the NOT EXISTS, which says nothing
        // about production where the log table is large.
        jdbc.update("insert into notification_logs (user_id, task_id, reminder_type, scheduled_at, status, attempts,"
                + " created_at, updated_at) select t.user_id, t.id, elt(1 + (k.j % 4), 'H24', 'H6', 'H1', 'AT_DEADLINE'),"
                + " t.deadline - interval (k.j div 4) minute - interval elt(1 + (k.j % 4), 24, 6, 1, 0) hour, 'SENT', 1,"
                + " now(3), now(3) from tasks t join (select 0 j union select 1 union select 2 union select 3 union"
                + " select 4 union select 5 union select 6 union select 7 union select 8 union select 9 union select 10"
                + " union select 11) k");
        jdbc.execute("analyze table tasks, users, reminder_settings, notification_logs");

        List<Map<String, Object>> plan = jdbc.queryForList("explain " + repository.candidateSql(ReminderType.H1),
                repository.bindingsFor(ReminderType.H1, NOW.minus(WINDOW), NOW, 200));
        plan.forEach(row -> System.out.println("EXPLAIN " + row));

        Map<String, Object> tasksRow = plan.stream().filter(r -> "t".equals(r.get("table"))).findFirst().orElseThrow();
        assertThat(tasksRow.get("type")).isEqualTo("range");
        assertThat(tasksRow.get("key")).isEqualTo("ix_tasks_status_deadline");
        // the NOT EXISTS probe must use the unique key per task, not scan or materialize the log table
        Map<String, Object> logsRow = plan.stream().filter(r -> "n".equals(r.get("table"))).findFirst().orElseThrow();
        assertThat(logsRow.get("type")).isEqualTo("eq_ref");
        assertThat(logsRow.get("key")).isEqualTo("uk_nlogs_task_type_sched");
    }
}
