package com.studenttask.task.service;

import com.studenttask.common.error.AppException;
import com.studenttask.common.error.ErrorCode;
import com.studenttask.task.dto.TaskFilter;
import com.studenttask.task.dto.TaskRequest;
import com.studenttask.task.dto.TaskResponse;
import com.studenttask.task.entity.Task;
import com.studenttask.task.entity.TaskStatus;
import com.studenttask.task.mapper.TaskMapper;
import com.studenttask.task.repository.TaskRepository;
import com.studenttask.task.repository.TaskSpecifications;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskService {

    private static final String STALE_VERSION =
            "Tugas telah diubah di tempat lain. Muat ulang lalu coba lagi.";

    private final TaskRepository tasks;
    private final TaskMapper mapper;
    private final Clock clock;
    private final UserZoneResolver zones;

    public TaskService(TaskRepository tasks, TaskMapper mapper, Clock clock, UserZoneResolver zones) {
        this.tasks = tasks;
        this.mapper = mapper;
        this.clock = clock;
        this.zones = zones;
    }

    @Transactional
    public TaskResponse create(long userId, TaskRequest request) {
        Instant now = now();
        Task task = new Task(userId, now);
        apply(task, request);
        task.changeStatus(request.status() == null ? TaskStatus.TODO : request.status(), now);
        return respond(userId, tasks.save(task));
    }

    @Transactional(readOnly = true)
    public TaskResponse get(long userId, long id) {
        return respond(userId, find(userId, id));
    }

    /** Filtered, sorted page of the user's own tasks; ordering comes from the pageable. */
    @Transactional(readOnly = true)
    public Page<TaskResponse> list(long userId, TaskFilter filter, Pageable pageable) {
        ZoneId zone = zones.zoneOf(userId);
        Instant now = now();
        return tasks.findAll(TaskSpecifications.forUser(userId, filter), pageable)
                .map(task -> mapper.toResponse(task, zone, now));
    }

    @Transactional(readOnly = true)
    public List<String> subjects(long userId) {
        return tasks.findDistinctSubjects(userId);
    }

    /** Full replace. A null status keeps the current one; a non-null request version must match. */
    @Transactional
    public TaskResponse update(long userId, long id, TaskRequest request) {
        Task task = find(userId, id);
        if (request.version() != null && request.version() != task.getVersion()) {
            throw new AppException(ErrorCode.CONFLICT, STALE_VERSION);
        }
        Instant now = now();
        apply(task, request);
        if (request.status() != null) {
            task.changeStatus(request.status(), now);
        }
        task.touch(now);
        return respond(userId, tasks.saveAndFlush(task));
    }

    @Transactional
    public TaskResponse updateStatus(long userId, long id, TaskStatus status) {
        Task task = find(userId, id);
        Instant now = now();
        task.changeStatus(status, now);
        task.touch(now);
        return respond(userId, tasks.saveAndFlush(task));
    }

    @Transactional
    public void delete(long userId, long id) {
        tasks.delete(find(userId, id));
    }

    private TaskResponse respond(long userId, Task task) {
        return mapper.toResponse(task, zones.zoneOf(userId), now());
    }

    private Task find(long userId, long id) {
        return tasks.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Tugas tidak ditemukan."));
    }

    private static void apply(Task task, TaskRequest request) {
        task.setTitle(request.title());
        task.setDescription(request.description());
        task.setSubject(request.subject());
        task.setDeadline(request.deadline().truncatedTo(ChronoUnit.MILLIS));
        task.setPriority(request.priority());
    }

    /** Millisecond precision to match DATETIME(3), so responses equal what is stored. */
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }
}
