package com.studenttask.user.dto;

public record ReminderSettingsResponse(
        boolean reminder24Hours, boolean reminder6Hours, boolean reminder1Hour, boolean reminderAtDeadline) {
}
