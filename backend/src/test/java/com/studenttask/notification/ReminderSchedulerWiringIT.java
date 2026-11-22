package com.studenttask.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.studenttask.MySqlTestBase;
import com.studenttask.scheduler.LogCleanupJob;
import com.studenttask.scheduler.ReminderScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

/** With {@code app.reminder.enabled=false} (test default) nothing triggers the jobs on its own. */
class ReminderSchedulerWiringIT extends MySqlTestBase {

    @Autowired
    ApplicationContext context;

    @Test
    void disabled_flag_registers_no_scheduled_trigger_but_keeps_the_beans_callable() {
        assertThat(context.getBeansOfType(ScheduledAnnotationBeanPostProcessor.class)).isEmpty();
        assertThat(context.getBeansOfType(ReminderScheduler.class)).hasSize(1);
        assertThat(context.getBeansOfType(LogCleanupJob.class)).hasSize(1);
    }
}
