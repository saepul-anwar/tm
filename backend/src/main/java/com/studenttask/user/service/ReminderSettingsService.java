package com.studenttask.user.service;

import com.studenttask.user.dto.ReminderSettingsRequest;
import com.studenttask.user.dto.ReminderSettingsResponse;
import com.studenttask.user.entity.ReminderSettings;
import com.studenttask.user.mapper.ProfileMapper;
import com.studenttask.user.repository.ReminderSettingsRepository;
import java.time.Clock;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Each repository call runs in its own short transaction, so a lost insert race (unique user_id) can be caught
 * and resolved by re-reading. Registration normally creates the row; a missing row is repaired with defaults.
 */
@Service
public class ReminderSettingsService {

    private final ReminderSettingsRepository repository;
    private final ProfileMapper mapper;
    private final Clock clock;

    public ReminderSettingsService(ReminderSettingsRepository repository, ProfileMapper mapper, Clock clock) {
        this.repository = repository;
        this.mapper = mapper;
        this.clock = clock;
    }

    public ReminderSettingsResponse get(long userId) {
        return mapper.toSettings(loadOrCreate(userId));
    }

    public ReminderSettingsResponse update(long userId, ReminderSettingsRequest request) {
        ReminderSettings settings = loadOrCreate(userId);
        settings.update(request.reminder24Hours(), request.reminder6Hours(), request.reminder1Hour(),
                request.reminderAtDeadline(), clock.instant());
        return mapper.toSettings(repository.save(settings));
    }

    private ReminderSettings loadOrCreate(long userId) {
        return repository.findByUserId(userId).orElseGet(() -> {
            try {
                return repository.saveAndFlush(ReminderSettings.allEnabled(userId, clock.instant()));
            } catch (DataIntegrityViolationException e) {
                return repository.findByUserId(userId).orElseThrow(() -> e); // created concurrently
            }
        });
    }
}
