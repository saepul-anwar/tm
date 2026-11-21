package com.studenttask.notification.service;

import com.studenttask.config.AppProperties;
import com.studenttask.notification.entity.NotificationLog;
import com.studenttask.notification.entity.NotificationStatus;
import com.studenttask.notification.repository.NotificationLogRepository;
import com.studenttask.task.entity.Task;
import com.studenttask.task.entity.TaskStatus;
import com.studenttask.task.repository.TaskRepository;
import com.studenttask.task.service.UserZoneResolver;
import com.studenttask.telegram.client.TelegramClient;
import com.studenttask.telegram.client.TelegramException;
import com.studenttask.user.repository.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Sends one claimed reminder and records the outcome. Deliberately not transactional: the row is read, Telegram is
 * called with no transaction/connection held, and the result is written with a short conditional UPDATE that only
 * succeeds while this worker still owns the row (status SENDING and the same {@code locked_at} it read). A worker
 * whose claim was taken over after a stall therefore cannot overwrite the new owner.
 *
 * <p>Calling {@code dispatch} twice for the same id at the same time in one JVM sends once (in-flight guard); across
 * JVMs a second sender can only appear after the sending timeout, by design (at-least-once, see spec section 10).
 */
@Service
public class ReminderDispatcher {

    private static final Logger LOG = LoggerFactory.getLogger(ReminderDispatcher.class);

    static final int MAX_ERROR_LENGTH = 500;
    private static final int MIN_TITLE_LENGTH_TO_REDACT = 3;
    private static final String REDACTED = "[redacted]";
    private static final Pattern URL_LIKE = Pattern.compile("\\S*(://|api\\.telegram\\.org)\\S*", Pattern.CASE_INSENSITIVE);
    private static final Pattern BOT_TOKEN_LIKE = Pattern.compile("(?:bot)?\\d{6,}:[A-Za-z0-9_-]{20,}");

    private final NotificationLogRepository logs;
    private final TaskRepository tasks;
    private final UserRepository users;
    private final UserZoneResolver zones;
    private final TelegramClient telegram;
    private final ReminderMessageFormatter formatter;
    private final AppProperties props;
    private final Clock clock;
    private final Set<Long> inFlight = ConcurrentHashMap.newKeySet();

    public ReminderDispatcher(NotificationLogRepository logs, TaskRepository tasks, UserRepository users,
            UserZoneResolver zones, TelegramClient telegram, ReminderMessageFormatter formatter,
            AppProperties props, Clock clock) {
        this.logs = logs;
        this.tasks = tasks;
        this.users = users;
        this.zones = zones;
        this.telegram = telegram;
        this.formatter = formatter;
        this.props = props;
        this.clock = clock;
    }

    public void dispatch(long logId) {
        if (!inFlight.add(logId)) {
            return;
        }
        try {
            process(logId);
        } finally {
            inFlight.remove(logId);
        }
    }

    private void process(long logId) {
        NotificationLog log = logs.findById(logId).orElse(null);
        if (log == null || log.getStatus() != NotificationStatus.SENDING || log.getLockedAt() == null) {
            return;
        }
        Instant owner = log.getLockedAt();
        AppProperties.Reminder config = props.reminder();
        if (log.getAttempts() > config.maxAttempts()) {
            record(logId, logs.markFailed(logId, owner, "Maximum attempts exceeded", clock.instant()));
            return;
        }
        Task task = tasks.findById(log.getTaskId()).orElse(null);
        String skipReason = skipReason(log, task, config);
        if (skipReason != null) {
            record(logId, logs.markSkipped(logId, owner, skipReason, clock.instant()));
            return;
        }
        long chatId = users.findTelegramChatIdById(log.getUserId()).orElseThrow();
        String text = formatter.format(task, zones.zoneOf(log.getUserId()));
        try {
            telegram.sendMessage(chatId, text);
        } catch (TelegramException e) {
            fail(log, owner, task, chatId, e.kind() == TelegramException.Kind.PERMANENT, e.retryAfter(), e.getMessage(),
                    config);
            return;
        } catch (RuntimeException e) {
            // Message deliberately dropped: unknown exceptions may carry URLs, ids or the title.
            fail(log, owner, task, chatId, false, null, "Unexpected error: " + e.getClass().getSimpleName(), config);
            return;
        }
        Instant now = clock.instant();
        record(logId, logs.markSent(logId, owner, now));
    }

    /** Null when the reminder should be sent; otherwise a fixed reason (no user data) stored in error_message. */
    private String skipReason(NotificationLog log, Task task, AppProperties.Reminder config) {
        if (task == null) {
            return "Task no longer exists";
        }
        if (task.getStatus() == TaskStatus.COMPLETED) {
            return "Task completed";
        }
        Instant plannedFor = task.getDeadline().minus(log.getReminderType().offset());
        if (!plannedFor.truncatedTo(ChronoUnit.MILLIS).equals(log.getScheduledAt().truncatedTo(ChronoUnit.MILLIS))) {
            return "Deadline changed since this reminder was scheduled";
        }
        if (users.findTelegramChatIdById(log.getUserId()).isEmpty()) {
            return "Telegram not connected";
        }
        // First attempt: late relative to the planned time. Retries: still useful until shortly after the deadline.
        Instant lastUseful = (log.getAttempts() <= 1 ? log.getScheduledAt() : task.getDeadline())
                .plus(config.catchUpWindow());
        if (clock.instant().isAfter(lastUseful)) {
            return "Outside the catch-up window";
        }
        return null;
    }

    private void fail(NotificationLog log, Instant owner, Task task, long chatId, boolean permanent,
            Duration retryAfter, String rawMessage, AppProperties.Reminder config) {
        long id = log.getId();
        Instant now = clock.instant();
        String error = sanitize(rawMessage, chatId, task.getTitle());
        if (permanent || log.getAttempts() >= config.maxAttempts()) {
            record(id, logs.markFailed(id, owner, error, now));
            return;
        }
        List<Duration> backoff = config.retryBackoff();
        Duration wait = backoff.get(Math.min(Math.max(log.getAttempts(), 1), backoff.size()) - 1);
        if (retryAfter != null && retryAfter.compareTo(wait) > 0) {
            wait = retryAfter;
        }
        record(id, logs.markRetry(id, owner, now.plus(wait), error, now));
    }

    /** A 0 means another worker owns the row now; this worker's outcome is dropped on purpose. */
    private static void record(long logId, int affected) {
        if (affected != 1) {
            LOG.warn("Reminder log {} was taken over by another worker; result not recorded", logId);
        }
    }

    /** Removes anything that must never be stored: URLs, bot-token-like strings, the chat id and the task title. */
    String sanitize(String raw, long chatId, String title) {
        String s = raw == null ? "" : raw;
        String botToken = props.telegram().botToken();
        if (botToken != null && !botToken.isBlank()) {
            s = s.replace(botToken, REDACTED);
        }
        s = URL_LIKE.matcher(s).replaceAll(REDACTED);
        s = BOT_TOKEN_LIKE.matcher(s).replaceAll(REDACTED);
        s = s.replace(String.valueOf(chatId), REDACTED);
        if (title != null && title.length() >= MIN_TITLE_LENGTH_TO_REDACT) {
            s = s.replace(title, REDACTED);
        }
        if (s.length() > MAX_ERROR_LENGTH) {
            int end = MAX_ERROR_LENGTH;
            if (Character.isHighSurrogate(s.charAt(end - 1))) {
                end--;
            }
            s = s.substring(0, end);
        }
        return s.isBlank() ? "Telegram send failed" : s;
    }
}
