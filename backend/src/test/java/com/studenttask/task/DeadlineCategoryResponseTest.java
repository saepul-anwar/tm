package com.studenttask.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.studenttask.task.dto.TaskRequest;
import com.studenttask.task.entity.DeadlineCategory;
import com.studenttask.task.entity.Priority;
import com.studenttask.task.entity.Task;
import com.studenttask.task.entity.TaskStatus;
import com.studenttask.task.mapper.TaskMapper;
import com.studenttask.task.repository.TaskRepository;
import com.studenttask.task.service.TaskService;
import com.studenttask.task.service.UserZoneResolver;
import com.studenttask.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** The service fills the category with the user's zone and the injected clock; invalid zones fall back. */
class DeadlineCategoryResponseTest {

    private static final Instant NOW = Instant.parse("2026-11-20T20:00:00Z");
    private final TaskRepository repo = mock(TaskRepository.class);
    private final UserRepository users = mock(UserRepository.class);

    private TaskService service(String timezone) {
        when(users.findTimezoneById(7L)).thenReturn(Optional.ofNullable(timezone));
        when(repo.save(any(Task.class))).thenAnswer(inv -> {
            Task saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 5L);
            return saved;
        });
        return new TaskService(repo, new TaskMapper(), Clock.fixed(NOW, ZoneOffset.UTC), new UserZoneResolver(users));
    }

    private static TaskRequest request(String deadline) {
        return new TaskRequest("Laporan", null, null, Instant.parse(deadline), Priority.LOW, TaskStatus.TODO, null);
    }

    // 2026-11-21T10:00Z is local "today" in Jakarta (already 21 Nov 03:00) but "tomorrow" in New York.
    @Test
    void category_uses_the_users_timezone() {
        assertThat(service("Asia/Jakarta").create(7L, request("2026-11-21T10:00:00Z")).deadlineCategory())
                .isEqualTo(DeadlineCategory.DUE_TODAY);
        assertThat(service("America/New_York").create(7L, request("2026-11-21T10:00:00Z")).deadlineCategory())
                .isEqualTo(DeadlineCategory.DUE_TOMORROW);
    }

    @Test
    void invalid_missing_or_blank_timezone_falls_back_to_jakarta() {
        for (String tz : new String[] {"Not/AZone", "", "  ", null}) {
            assertThat(service(tz).create(7L, request("2026-11-21T10:00:00Z")).deadlineCategory())
                    .as("timezone=%s", tz).isEqualTo(DeadlineCategory.DUE_TODAY);
        }
    }
}
