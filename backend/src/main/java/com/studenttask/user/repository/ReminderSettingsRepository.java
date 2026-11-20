package com.studenttask.user.repository;

import com.studenttask.user.entity.ReminderSettings;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReminderSettingsRepository extends JpaRepository<ReminderSettings, Long> {

    Optional<ReminderSettings> findByUserId(long userId);
}
