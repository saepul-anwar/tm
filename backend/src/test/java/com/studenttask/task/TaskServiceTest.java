package com.studenttask.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.studenttask.common.error.AppException;
import com.studenttask.common.error.ErrorCode;
import com.studenttask.task.dto.TaskRequest;
import com.studenttask.task.dto.TaskResponse;
import com.studenttask.task.entity.DeadlineCategory;
import com.studenttask.task.entity.Priority;
import com.studenttask.task.entity.Task;
import com.studenttask.task.entity.TaskStatus;
import com.studenttask.task.mapper.TaskMapper;
import com.studenttask.task.repository.TaskRepository;
import com.studenttask.task.service.TaskService;
import com.studenttask.task.service.UserZoneResolver;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class TaskServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-20T10:00:00.123456Z");
    private static final Instant NOW_MILLIS = Instant.parse("2026-11-20T10:00:00.123Z");
    private static final long USER = 7L;

    private final TaskRepository repo = mock(TaskRepository.class);
    private final UserZoneResolver zones = mock(UserZoneResolver.class);
    private TaskService service;

    @BeforeEach
    void setUp() {
        service = new TaskService(repo, new TaskMapper(), Clock.fixed(NOW, ZoneOffset.UTC), zones);
        when(zones.zoneOf(USER)).thenReturn(ZoneId.of("Asia/Jakarta"));
        when(repo.saveAndFlush(any(Task.class))).thenAnswer(inv -> repo.save(inv.getArgument(0)));
        when(repo.save(any(Task.class))).thenAnswer(inv -> {
            Task saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 5L);
            return saved;
        });
    }

    private static TaskRequest request(TaskStatus status, Long version) {
        return new TaskRequest("Laporan", "desc", "Kalkulus", Instant.parse("2020-01-01T00:00:00Z"),
                Priority.HIGH, status, version);
    }

    private Task existing(TaskStatus status, Instant completedAt, long version) {
        Task task = new Task(USER, NOW_MILLIS);
        task.setTitle("Lama");
        task.setDeadline(Instant.parse("2030-01-01T00:00:00Z"));
        task.setPriority(Priority.LOW);
        ReflectionTestUtils.setField(task, "status", status);
        ReflectionTestUtils.setField(task, "completedAt", completedAt);
        ReflectionTestUtils.setField(task, "version", version);
        when(repo.findByIdAndUserId(5L, USER)).thenReturn(Optional.of(task));
        return task;
    }

    @Test
    void create_allows_past_deadline_defaults_status_todo_and_uses_clock_in_millis() {
        TaskResponse response = service.create(USER, request(null, null));

        assertThat(response.status()).isEqualTo(TaskStatus.TODO);
        assertThat(response.deadline()).isEqualTo(Instant.parse("2020-01-01T00:00:00Z"));
        assertThat(response.createdAt()).isEqualTo(NOW_MILLIS);
        assertThat(response.completedAt()).isNull();
        // deadline 2020 is in the past for an open task
        assertThat(response.deadlineCategory()).isEqualTo(DeadlineCategory.OVERDUE);
    }

    @Test
    void create_owner_comes_from_the_argument_only() {
        service.create(USER, request(null, null));
        org.mockito.ArgumentCaptor<Task> captor = org.mockito.ArgumentCaptor.forClass(Task.class);
        verify(repo).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(USER);
    }

    @Test
    void create_with_completed_status_sets_completed_at() {
        assertThat(service.create(USER, request(TaskStatus.COMPLETED, null)).completedAt()).isEqualTo(NOW_MILLIS);
    }

    @Test
    void status_completed_sets_completed_at_and_reopen_clears_it() {
        existing(TaskStatus.TODO, null, 0);
        assertThat(service.updateStatus(USER, 5L, TaskStatus.COMPLETED).completedAt()).isEqualTo(NOW_MILLIS);

        existing(TaskStatus.COMPLETED, NOW_MILLIS, 1);
        TaskResponse reopened = service.updateStatus(USER, 5L, TaskStatus.IN_PROGRESS);
        assertThat(reopened.completedAt()).isNull();
        assertThat(reopened.status()).isEqualTo(TaskStatus.IN_PROGRESS);
    }

    @Test
    void completing_an_already_completed_task_keeps_the_original_completed_at() {
        Instant original = Instant.parse("2026-11-01T00:00:00Z");
        existing(TaskStatus.COMPLETED, original, 1);
        assertThat(service.updateStatus(USER, 5L, TaskStatus.COMPLETED).completedAt()).isEqualTo(original);
    }

    @Test
    void update_with_stale_version_returns_409() {
        existing(TaskStatus.TODO, null, 3);
        assertThatThrownBy(() -> service.update(USER, 5L, request(null, 2L)))
                .isInstanceOfSatisfying(AppException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.CONFLICT));
        verify(repo, never()).save(any());
    }

    @Test
    void update_without_version_skips_the_check_and_keeps_status_when_omitted() {
        existing(TaskStatus.IN_PROGRESS, null, 3);
        TaskResponse response = service.update(USER, 5L, request(null, null));
        assertThat(response.title()).isEqualTo("Laporan");
        assertThat(response.status()).isEqualTo(TaskStatus.IN_PROGRESS);
    }

    @Test
    void update_with_matching_version_and_completed_status_sets_completed_at() {
        existing(TaskStatus.TODO, null, 3);
        assertThat(service.update(USER, 5L, request(TaskStatus.COMPLETED, 3L)).completedAt())
                .isEqualTo(NOW_MILLIS);
    }

    @Test
    void missing_or_foreign_task_is_not_found_for_get_update_patch_delete() {
        when(repo.findByIdAndUserId(99L, USER)).thenReturn(Optional.empty());
        assertNotFound(() -> service.get(USER, 99L));
        assertNotFound(() -> service.update(USER, 99L, request(null, null)));
        assertNotFound(() -> service.updateStatus(USER, 99L, TaskStatus.COMPLETED));
        assertNotFound(() -> service.delete(USER, 99L));
        verify(repo, never()).delete(any(Task.class));
        verify(repo, never()).findById(any());
    }

    @Test
    void delete_removes_the_owned_task() {
        Task task = existing(TaskStatus.TODO, null, 0);
        service.delete(USER, 5L);
        verify(repo).delete(task);
    }

    private static void assertNotFound(Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(AppException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND));
    }
}
