package com.studenttask.task.dto;

import com.studenttask.task.entity.Priority;
import com.studenttask.task.entity.TaskStatus;
import java.time.Instant;

/**
 * Optional list filters; a null field means "no filter". {@code deadlineFrom} is inclusive and
 * {@code deadlineTo} is exclusive, so adjacent ranges never overlap.
 */
public record TaskFilter(
        String q,
        String subject,
        TaskStatus status,
        Priority priority,
        Instant deadlineFrom,
        Instant deadlineTo) {

    public static TaskFilter none() {
        return new TaskFilter(null, null, null, null, null, null);
    }
}
