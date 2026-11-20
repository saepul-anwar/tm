package com.studenttask.task.mapper;

import com.studenttask.task.dto.TaskResponse;
import com.studenttask.task.entity.Task;
import com.studenttask.task.service.DeadlineCategoryCalculator;
import java.time.Instant;
import java.time.ZoneId;
import org.springframework.stereotype.Component;

@Component
public class TaskMapper {

    /** {@code zone} is the owner's zone and {@code now} the injected clock's instant (for the category). */
    public TaskResponse toResponse(Task task, ZoneId zone, Instant now) {
        return new TaskResponse(task.getId(), task.getTitle(), task.getDescription(), task.getSubject(),
                task.getDeadline(), task.getPriority(), task.getStatus(), task.getCompletedAt(),
                task.getCreatedAt(), task.getUpdatedAt(), task.getVersion(),
                DeadlineCategoryCalculator.categorize(task.getDeadline(), task.getStatus(), zone, now));
    }
}
