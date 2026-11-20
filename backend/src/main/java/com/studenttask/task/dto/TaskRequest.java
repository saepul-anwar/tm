package com.studenttask.task.dto;

import com.studenttask.task.entity.Priority;
import com.studenttask.task.entity.TaskStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * Create/replace payload. There is deliberately no user id. {@code deadline} is ISO-8601 with offset and is
 * normalised to UTC; {@code version} is optional and only used by PUT for optimistic locking.
 */
public record TaskRequest(
        @NotBlank(message = "Judul wajib diisi.") @Size(max = 200, message = "Judul maksimal 200 karakter.")
        String title,
        @Size(max = 5000, message = "Deskripsi maksimal 5000 karakter.") String description,
        @Size(max = 100, message = "Mata kuliah maksimal 100 karakter.") String subject,
        @NotNull(message = "Deadline wajib diisi.") Instant deadline,
        @NotNull(message = "Prioritas wajib diisi.") Priority priority,
        TaskStatus status,
        Long version) {

    public TaskRequest {
        title = title == null ? null : title.strip();
        subject = subject == null || subject.isBlank() ? null : subject.strip();
    }
}
