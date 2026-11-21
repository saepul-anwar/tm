package com.studenttask.task.dto;

import com.studenttask.task.entity.DeadlineCategory;
import com.studenttask.task.entity.Priority;
import com.studenttask.task.entity.TaskStatus;
import java.time.Instant;

/** All instants are UTC. {@code deadlineCategory} is null for COMPLETED tasks. */
public record TaskResponse(
        long id,
        String title,
        String description,
        String subject,
        Instant deadline,
        Priority priority,
        TaskStatus status,
        Instant completedAt,
        Instant createdAt,
        Instant updatedAt,
        long version,
        DeadlineCategory deadlineCategory) {
}
