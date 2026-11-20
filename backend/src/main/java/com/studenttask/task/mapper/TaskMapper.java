package com.studenttask.task.mapper;

import com.studenttask.task.dto.TaskResponse;
import com.studenttask.task.entity.Task;
import org.springframework.stereotype.Component;

@Component
public class TaskMapper {

    public TaskResponse toResponse(Task task) {
        return new TaskResponse(task.getId(), task.getTitle(), task.getDescription(), task.getSubject(),
                task.getDeadline(), task.getPriority(), task.getStatus(), task.getCompletedAt(),
                task.getCreatedAt(), task.getUpdatedAt(), task.getVersion(), null);
    }
}
