package com.studenttask.task.service;

import com.studenttask.task.dto.DashboardCounts;
import com.studenttask.task.dto.SummaryResponse;
import com.studenttask.task.dto.TaskResponse;
import com.studenttask.task.entity.Task;
import com.studenttask.task.entity.TaskStatus;
import com.studenttask.task.mapper.TaskMapper;
import com.studenttask.task.repository.TaskRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Definitions (all relative to the injected clock's "now", scoped to one user):
 * overdue = open task with deadline &lt; now (a subset of todo + inProgress, not a status);
 * upcoming = open task with deadline in [now, now + 7 days]; open = status is not COMPLETED.
 */
@Service
public class DashboardService {

    static final int HIGHLIGHT_LIMIT = 5;
    static final int LIST_LIMIT = 50;

    private static final Pageable HIGHLIGHTS = PageRequest.of(0, HIGHLIGHT_LIMIT);
    private static final Pageable LIST = PageRequest.of(0, LIST_LIMIT);

    private final TaskRepository tasks;
    private final TaskMapper mapper;
    private final Clock clock;
    private final UserZoneResolver zones;

    public DashboardService(TaskRepository tasks, TaskMapper mapper, Clock clock, UserZoneResolver zones) {
        this.tasks = tasks;
        this.mapper = mapper;
        this.clock = clock;
        this.zones = zones;
    }

    @Transactional(readOnly = true)
    public SummaryResponse summary(long userId) {
        Instant now = now();
        Instant horizon = now.plus(DeadlineCategoryCalculator.UPCOMING_WINDOW);
        ZoneId zone = zones.zoneOf(userId);

        long todo = 0;
        long inProgress = 0;
        long completed = 0;
        for (Object[] row : tasks.countByStatus(userId)) {
            long count = (Long) row[1];
            switch ((TaskStatus) row[0]) {
                case TODO -> todo = count;
                case IN_PROGRESS -> inProgress = count;
                case COMPLETED -> completed = count;
            }
        }
        long total = todo + inProgress + completed;
        DashboardCounts counts = new DashboardCounts(total, todo, inProgress, completed,
                tasks.countOverdue(userId, TaskStatus.COMPLETED, now),
                tasks.countOpenDueBetween(userId, TaskStatus.COMPLETED, now, horizon));
        return new SummaryResponse(counts, completionPercent(completed, total),
                respond(tasks.findNextDue(userId, TaskStatus.COMPLETED, now, HIGHLIGHTS), zone, now),
                respond(tasks.findTopPriority(userId, TaskStatus.COMPLETED, HIGHLIGHTS), zone, now));
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> upcoming(long userId) {
        Instant now = now();
        return respond(tasks.findOpenDueBetween(userId, TaskStatus.COMPLETED, now,
                now.plus(DeadlineCategoryCalculator.UPCOMING_WINDOW), LIST), zones.zoneOf(userId), now);
    }

    /** Most overdue (oldest deadline) first. */
    @Transactional(readOnly = true)
    public List<TaskResponse> overdue(long userId) {
        Instant now = now();
        return respond(tasks.findOverdue(userId, TaskStatus.COMPLETED, now, LIST), zones.zoneOf(userId), now);
    }

    /** Integer percentage, rounded half up (e.g. 2/7 = 28.57 gives 29); 0 when there are no tasks. */
    static int completionPercent(long completed, long total) {
        if (total == 0) {
            return 0;
        }
        return (int) ((completed * 100 + total / 2) / total);
    }

    private List<TaskResponse> respond(List<Task> rows, ZoneId zone, Instant now) {
        return rows.stream().map(task -> mapper.toResponse(task, zone, now)).toList();
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }
}
