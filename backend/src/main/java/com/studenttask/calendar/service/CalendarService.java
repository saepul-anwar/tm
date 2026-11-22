package com.studenttask.calendar.service;

import com.studenttask.calendar.dto.CalendarTaskResponse;
import com.studenttask.common.api.FieldError;
import com.studenttask.common.error.AppException;
import com.studenttask.common.error.ErrorCode;
import com.studenttask.task.repository.TaskRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CalendarService {

    /** Defensive cap: the earliest MAX_RESULTS tasks of the range are returned; there is no pagination. */
    public static final int MAX_RESULTS = 1000;
    public static final Duration MAX_RANGE = Duration.ofDays(62);

    private final TaskRepository tasks;

    public CalendarService(TaskRepository tasks) {
        this.tasks = tasks;
    }

    /** Tasks of {@code userId} with deadline in [from, to), any status, by deadline then id. */
    @Transactional(readOnly = true)
    public List<CalendarTaskResponse> tasks(long userId, Instant from, Instant to) {
        if (from == null || to == null || !from.isBefore(to)) {
            throw invalid("to", "Parameter to harus setelah from.");
        }
        if (Duration.between(from, to).compareTo(MAX_RANGE) > 0) {
            throw invalid("to", "Rentang maksimal " + MAX_RANGE.toDays() + " hari.");
        }
        return tasks.findCalendarTasks(userId, from, to, PageRequest.of(0, MAX_RESULTS));
    }

    private static AppException invalid(String field, String message) {
        return new AppException(ErrorCode.VALIDATION_ERROR, message, List.of(new FieldError(field, message)));
    }
}
