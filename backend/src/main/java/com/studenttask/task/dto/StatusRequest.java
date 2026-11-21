package com.studenttask.task.dto;

import com.studenttask.task.entity.TaskStatus;
import jakarta.validation.constraints.NotNull;

public record StatusRequest(@NotNull(message = "Status wajib diisi.") TaskStatus status) {
}
