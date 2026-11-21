package com.studenttask.calendar.controller;

import com.studenttask.calendar.dto.CalendarTaskResponse;
import com.studenttask.calendar.service.CalendarService;
import com.studenttask.common.api.ApiResponse;
import com.studenttask.common.api.FieldError;
import com.studenttask.common.error.AppException;
import com.studenttask.common.error.ErrorCode;
import com.studenttask.security.SecurityUtils;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/calendar")
public class CalendarController {

    private final CalendarService service;

    public CalendarController(CalendarService service) {
        this.service = service;
    }

    /** from is inclusive, to exclusive; both ISO-8601 date-time with Z or an offset (encode + as %2B). */
    @GetMapping("/tasks")
    public ApiResponse<List<CalendarTaskResponse>> tasks(
            @RequestParam(required = false) String from, @RequestParam(required = false) String to) {
        Instant start = parseInstant("from", from);
        Instant end = parseInstant("to", to);
        return ApiResponse.ok(service.tasks(SecurityUtils.currentUserId(), start, end));
    }

    private static Instant parseInstant(String name, String value) {
        if (value == null || value.isBlank()) {
            throw invalid(name, "Parameter " + name + " wajib diisi.");
        }
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException e) {
            throw invalid(name,
                    "Parameter " + name + " harus berformat ISO-8601 dengan zona, mis. 2026-12-01T00:00:00Z.");
        }
    }

    private static AppException invalid(String field, String message) {
        return new AppException(ErrorCode.VALIDATION_ERROR, message, List.of(new FieldError(field, message)));
    }
}
