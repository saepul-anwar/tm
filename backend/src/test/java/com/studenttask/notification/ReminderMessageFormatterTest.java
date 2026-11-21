package com.studenttask.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.studenttask.notification.service.ReminderMessageFormatter;
import com.studenttask.task.entity.Priority;
import com.studenttask.task.entity.Task;
import com.studenttask.task.entity.TaskStatus;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class ReminderMessageFormatterTest {

    private static final Instant DEADLINE = Instant.parse("2026-11-21T07:30:00Z");
    private final ReminderMessageFormatter formatter = new ReminderMessageFormatter();

    private static Task task(String title, String subject) {
        Task t = new Task(1L, Instant.parse("2026-11-20T00:00:00Z"));
        t.setTitle(title);
        t.setSubject(subject);
        t.setDeadline(DEADLINE);
        t.setPriority(Priority.HIGH);
        t.changeStatus(TaskStatus.TODO, Instant.parse("2026-11-20T00:00:00Z"));
        return t;
    }

    @Test
    void formatter_outputs_spec_template_with_WIB_for_jakarta_and_literal_title() {
        String text = formatter.format(task("Laporan Praktikum 3", "Basis Data"), ZoneId.of("Asia/Jakarta"));

        assertThat(text).isEqualTo(String.join("\n",
                "🔔 STUDENT TASK REMINDER",
                "",
                "Halo, kamu memiliki tugas yang akan segera deadline!",
                "",
                "📚 Mata Kuliah: Basis Data",
                "📝 Tugas: Laporan Praktikum 3",
                "⚡ Prioritas: HIGH",
                "📅 Deadline: 21 November 2026",
                "🕐 Waktu: 14:30 WIB",
                "",
                "Segera selesaikan tugasmu sebelum deadline!"));
    }

    @Test
    void user_timezone_Asia_Makassar_shows_WITA_and_Jayapura_shows_WIT() {
        assertThat(formatter.format(task("x", "y"), ZoneId.of("Asia/Makassar"))).contains("Waktu: 15:30 WITA");
        assertThat(formatter.format(task("x", "y"), ZoneId.of("Asia/Jayapura"))).contains("Waktu: 16:30 WIT")
                .doesNotContain("WITA");
    }

    @Test
    void other_zone_uses_its_abbreviation_and_local_date() {
        String text = formatter.format(task("x", "y"), ZoneId.of("America/New_York"));

        assertThat(text).contains("Deadline: 21 November 2026").contains("Waktu: 02:30 EST");
    }

    @Test
    void local_date_can_differ_from_utc_date() {
        Task t = task("x", "y");
        t.setDeadline(Instant.parse("2026-12-31T20:00:00Z"));

        assertThat(formatter.format(t, ZoneId.of("Asia/Jakarta"))).contains("Deadline: 1 Januari 2027")
                .contains("Waktu: 03:00 WIB");
    }

    @Test
    void missing_subject_is_a_dash() {
        assertThat(formatter.format(task("x", null), ZoneId.of("Asia/Jakarta"))).contains("Mata Kuliah: -\n");
    }

    @Test
    void formatter_does_not_set_parse_mode() {
        String title = "<b>x</b> *x* _y_ [a](http://b) `c` & \"q\" <script>alert(1)</script>";

        String text = formatter.format(task(title, "<i>s</i>"), ZoneId.of("Asia/Jakarta"));

        assertThat(text).contains("Tugas: " + title + "\n").contains("Mata Kuliah: <i>s</i>\n")
                .doesNotContain("&lt;").doesNotContain("&amp;").doesNotContain("\\*").doesNotContain("\\_");
    }

    @Test
    void very_long_title_is_capped_with_ellipsis_without_splitting_emoji() {
        String title = "😀".repeat(300);

        String text = formatter.format(task(title, "y"), ZoneId.of("Asia/Jakarta"));
        String line = text.lines().filter(l -> l.contains("Tugas: ")).findFirst().orElseThrow();
        String shown = line.substring(line.indexOf("Tugas: ") + "Tugas: ".length());

        assertThat(shown.codePointCount(0, shown.length())).isEqualTo(200);
        assertThat(shown).endsWith("…");
        assertThat(Character.isHighSurrogate(shown.charAt(shown.length() - 2))).isFalse();
        assertThat(Character.isLowSurrogate(shown.charAt(shown.length() - 2))).isTrue();
    }

    @Test
    void title_of_exactly_200_chars_is_unchanged() {
        String title = "a".repeat(200);

        assertThat(formatter.format(task(title, "y"), ZoneId.of("Asia/Jakarta"))).contains("Tugas: " + title + "\n");
    }
}
