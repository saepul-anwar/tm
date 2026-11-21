package com.studenttask.notification.service;

import com.studenttask.task.entity.Task;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Builds the reminder text (spec section 15.3). The result is sent as plain text without any parse mode, so title and
 * subject are inserted literally: no escaping, and no transformation that could be mistaken for markup.
 */
@Component
public class ReminderMessageFormatter {

    /** Longest title/subject shown in a reminder (titles are limited to 200 characters when saved). */
    static final int MAX_TEXT_CODE_POINTS = 200;
    private static final String ELLIPSIS = "…";
    private static final String NO_SUBJECT = "-";

    private static final Locale INDONESIAN = Locale.forLanguageTag("id-ID");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", INDONESIAN);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);
    private static final DateTimeFormatter ZONE_ABBREVIATION = DateTimeFormatter.ofPattern("zzz", Locale.ENGLISH);
    private static final Map<String, String> INDONESIAN_ZONE_LABELS = Map.of(
            "Asia/Jakarta", "WIB",
            "Asia/Makassar", "WITA",
            "Asia/Jayapura", "WIT");

    public String format(Task task, ZoneId zone) {
        ZonedDateTime local = task.getDeadline().atZone(zone);
        String subject = task.getSubject() == null || task.getSubject().isBlank()
                ? NO_SUBJECT : cap(task.getSubject());
        return String.join("\n",
                "🔔 STUDENT TASK REMINDER",
                "",
                "Halo, kamu memiliki tugas yang akan segera deadline!",
                "",
                "📚 Mata Kuliah: " + subject,
                "📝 Tugas: " + cap(task.getTitle()),
                "⚡ Prioritas: " + task.getPriority().name(),
                "📅 Deadline: " + DATE.format(local),
                "🕐 Waktu: " + TIME.format(local) + " " + zoneLabel(zone, local),
                "",
                "Segera selesaikan tugasmu sebelum deadline!");
    }

    private static String zoneLabel(ZoneId zone, ZonedDateTime local) {
        String label = INDONESIAN_ZONE_LABELS.get(zone.getId());
        return label != null ? label : ZONE_ABBREVIATION.format(local);
    }

    /** Caps by code points so an emoji (surrogate pair) is never cut in half. */
    private static String cap(String value) {
        if (value.codePointCount(0, value.length()) <= MAX_TEXT_CODE_POINTS) {
            return value;
        }
        int end = value.offsetByCodePoints(0, MAX_TEXT_CODE_POINTS - 1);
        return value.substring(0, end) + ELLIPSIS;
    }
}
