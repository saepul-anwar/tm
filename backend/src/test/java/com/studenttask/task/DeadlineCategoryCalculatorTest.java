package com.studenttask.task;

import static com.studenttask.task.entity.DeadlineCategory.DUE_TODAY;
import static com.studenttask.task.entity.DeadlineCategory.DUE_TOMORROW;
import static com.studenttask.task.entity.DeadlineCategory.LATER;
import static com.studenttask.task.entity.DeadlineCategory.OVERDUE;
import static com.studenttask.task.entity.DeadlineCategory.UPCOMING;
import static org.assertj.core.api.Assertions.assertThat;

import com.studenttask.task.entity.DeadlineCategory;
import com.studenttask.task.entity.TaskStatus;
import com.studenttask.task.service.DeadlineCategoryCalculator;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class DeadlineCategoryCalculatorTest {

    private static final ZoneId JAKARTA = ZoneId.of("Asia/Jakarta");
    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");

    private static DeadlineCategory cat(String deadline, TaskStatus status, ZoneId zone, String now) {
        return DeadlineCategoryCalculator.categorize(Instant.parse(deadline), status, zone, Instant.parse(now));
    }

    private static DeadlineCategory open(String deadline, ZoneId zone, String now) {
        return cat(deadline, TaskStatus.TODO, zone, now);
    }

    @Test
    void deadline_equal_to_now_is_not_overdue_but_one_ms_before_is() {
        String now = "2026-11-20T05:00:00Z";
        assertThat(open("2026-11-20T05:00:00Z", JAKARTA, now)).isEqualTo(DUE_TODAY);
        assertThat(open("2026-11-20T04:59:59.999Z", JAKARTA, now)).isEqualTo(OVERDUE);
        assertThat(cat("2026-11-20T04:59:59.999Z", TaskStatus.IN_PROGRESS, JAKARTA, now)).isEqualTo(OVERDUE);
    }

    @Test
    void completed_task_has_null_category() {
        assertThat(cat("2020-01-01T00:00:00Z", TaskStatus.COMPLETED, JAKARTA, "2026-11-20T05:00:00Z")).isNull();
        assertThat(cat("2030-01-01T00:00:00Z", TaskStatus.COMPLETED, JAKARTA, "2026-11-20T05:00:00Z")).isNull();
    }

    @Test
    void local_midnight_boundary_today_vs_tomorrow_in_jakarta() {
        // now = 2026-11-20 10:00 WIB (UTC+7); local day ends at 2026-11-20T17:00:00Z.
        String now = "2026-11-20T03:00:00Z";
        assertThat(open("2026-11-20T16:59:59.999Z", JAKARTA, now)).isEqualTo(DUE_TODAY);
        assertThat(open("2026-11-20T17:00:00Z", JAKARTA, now)).isEqualTo(DUE_TOMORROW);
        assertThat(open("2026-11-21T16:59:59.999Z", JAKARTA, now)).isEqualTo(DUE_TOMORROW);
        assertThat(open("2026-11-21T17:00:00Z", JAKARTA, now)).isEqualTo(UPCOMING);
    }

    @Test
    void due_at_2359_local_today_vs_0000_local_tomorrow() {
        String now = "2026-11-20T03:00:00Z";
        assertThat(open("2026-11-20T16:59:00Z", JAKARTA, now)).isEqualTo(DUE_TODAY); // 23:59 WIB
        assertThat(open("2026-11-20T17:00:00Z", JAKARTA, now)).isEqualTo(DUE_TOMORROW); // 00:00 WIB
    }

    @Test
    void same_instant_gives_different_local_day_in_different_zones() {
        String now = "2026-11-20T20:00:00Z"; // Jakarta already 21 Nov 03:00, New York 20 Nov 15:00
        assertThat(open("2026-11-21T10:00:00Z", JAKARTA, now)).isEqualTo(DUE_TODAY);
        assertThat(open("2026-11-21T10:00:00Z", NEW_YORK, now)).isEqualTo(DUE_TOMORROW);
    }

    @Test
    void new_york_user_on_dst_change_day_gets_correct_category() {
        // Spring forward 2027-03-14: 02:00 EST -> 03:00 EDT (local day is 23h; midnight EST = 05:00Z).
        String springNow = "2027-03-14T12:00:00Z"; // 08:00 EDT
        assertThat(open("2027-03-15T03:59:59.999Z", NEW_YORK, springNow)).isEqualTo(DUE_TODAY); // 23:59:59 EDT
        assertThat(open("2027-03-15T04:00:00Z", NEW_YORK, springNow)).isEqualTo(DUE_TOMORROW); // 00:00 EDT
        assertThat(open("2027-03-16T04:00:00Z", NEW_YORK, springNow)).isEqualTo(UPCOMING);
        assertThat(open("2027-03-14T07:00:00Z", NEW_YORK, "2027-03-14T05:00:00Z")).isEqualTo(DUE_TODAY);

        // Fall back 2026-11-01: 02:00 EDT -> 01:00 EST (local day is 25h; midnight EDT = 04:00Z).
        String fallNow = "2026-11-01T12:00:00Z"; // 07:00 EST
        assertThat(open("2026-11-02T04:59:59.999Z", NEW_YORK, fallNow)).isEqualTo(DUE_TODAY); // 23:59:59 EST
        assertThat(open("2026-11-02T05:00:00Z", NEW_YORK, fallNow)).isEqualTo(DUE_TOMORROW); // 00:00 EST
        assertThat(open("2026-11-01T05:30:00Z", NEW_YORK, "2026-11-01T05:00:00Z")).isEqualTo(DUE_TODAY); // 01:30 EDT
        assertThat(open("2026-11-01T06:30:00Z", NEW_YORK, "2026-11-01T05:00:00Z")).isEqualTo(DUE_TODAY); // 01:30 EST
    }

    @Test
    void day_plus_8_is_later_and_day_plus_2_is_upcoming() {
        String now = "2026-11-20T03:00:00Z";
        assertThat(open("2026-11-22T03:00:00Z", JAKARTA, now)).isEqualTo(UPCOMING);
        assertThat(open("2026-11-28T03:00:00Z", JAKARTA, now)).isEqualTo(LATER);
    }

    @Test
    void seven_day_boundary_is_inclusive() {
        String now = "2026-11-20T03:00:00Z";
        assertThat(open("2026-11-27T03:00:00Z", JAKARTA, now)).isEqualTo(UPCOMING);
        assertThat(open("2026-11-27T03:00:00.001Z", JAKARTA, now)).isEqualTo(LATER);
    }

    @Test
    void overdue_takes_precedence_over_today() {
        assertThat(open("2026-11-20T00:00:00Z", JAKARTA, "2026-11-20T03:00:00Z")).isEqualTo(OVERDUE);
    }
}
