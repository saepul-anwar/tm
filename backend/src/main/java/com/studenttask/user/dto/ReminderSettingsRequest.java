package com.studenttask.user.dto;

import jakarta.validation.constraints.NotNull;

public record ReminderSettingsRequest(
        @NotNull(message = "Wajib diisi.") Boolean reminder24Hours,
        @NotNull(message = "Wajib diisi.") Boolean reminder6Hours,
        @NotNull(message = "Wajib diisi.") Boolean reminder1Hour,
        @NotNull(message = "Wajib diisi.") Boolean reminderAtDeadline) {
}
