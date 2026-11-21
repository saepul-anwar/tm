package com.studenttask.scheduler;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on {@code @Scheduled} processing only when {@code app.reminder.enabled} is true (the default). When it is
 * false nothing fires on its own; the job beans stay available for direct calls (tests, operations).
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(prefix = "app.reminder", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
