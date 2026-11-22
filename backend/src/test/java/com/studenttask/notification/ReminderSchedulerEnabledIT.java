package com.studenttask.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.studenttask.MySqlTestBase;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.scheduling.config.ScheduledTask;

/** Enabled flag: the tick is registered with the configured fixed delay and the cleanup with its cron. */
// poll-interval of one hour (also the initial delay) so nothing fires during the test
@SpringBootTest(properties = {"app.reminder.enabled=true", "app.reminder.send-pause=PT0S",
        "app.reminder.poll-interval=PT1H", "app.reminder.cleanup-cron=0 0 4 1 1 *"})
class ReminderSchedulerEnabledIT extends MySqlTestBase {

    @Autowired
    ScheduledAnnotationBeanPostProcessor processor;

    @Test
    void tick_is_scheduled_with_fixed_delay_and_cleanup_with_cron() {
        List<String> described = processor.getScheduledTasks().stream().map(ScheduledTask::getTask)
                .map(Object::toString).toList();

        assertThat(processor.getScheduledTasks().stream().map(ScheduledTask::getTask).toList())
                .anyMatch(t -> t instanceof FixedDelayTask f && f.getIntervalDuration().toMinutes() == 60
                        && f.getRunnable().toString().contains("ReminderScheduler.tick"))
                .anyMatch(t -> t instanceof CronTask c && c.getExpression().equals("0 0 4 1 1 *")
                        && c.getRunnable().toString().contains("LogCleanupJob"));
        assertThat(described).hasSize(2);
    }
}
