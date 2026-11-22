package com.studenttask.calendar.dto;

import com.studenttask.task.entity.Priority;
import com.studenttask.task.entity.TaskStatus;
import java.time.Instant;

/** Lightweight calendar item: no description and no owner id. {@code deadline} is UTC. */
public record CalendarTaskResponse(
        long id,
        String title,
        String subject,
        Instant deadline,
        Priority priority,
        TaskStatus status) {
}
