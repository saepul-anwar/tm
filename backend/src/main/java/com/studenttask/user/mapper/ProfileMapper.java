package com.studenttask.user.mapper;

import com.studenttask.user.dto.ProfileResponse;
import com.studenttask.user.dto.ReminderSettingsResponse;
import com.studenttask.user.dto.TelegramStatus;
import com.studenttask.user.entity.ReminderSettings;
import com.studenttask.user.entity.User;
import org.springframework.stereotype.Component;

/** Never exposes the password hash or the Telegram chat id. */
@Component
public class ProfileMapper {

    public ProfileResponse toProfile(User user) {
        return new ProfileResponse(user.getFullName(), user.getEmail(), user.getTimezone(),
                new TelegramStatus(user.isTelegramConnected(), user.getTelegramConnectedAt()));
    }

    public ReminderSettingsResponse toSettings(ReminderSettings s) {
        return new ReminderSettingsResponse(
                s.isReminder24Hours(), s.isReminder6Hours(), s.isReminder1Hour(), s.isReminderAtDeadline());
    }
}
