package com.studenttask.task.controller;

import com.studenttask.common.api.ApiResponse;
import com.studenttask.common.api.PageMeta;
import com.studenttask.common.error.AppException;
import com.studenttask.common.error.ErrorCode;
import com.studenttask.security.SecurityUtils;
import com.studenttask.task.dto.StatusRequest;
import com.studenttask.task.dto.TaskFilter;
import com.studenttask.task.dto.TaskRequest;
import com.studenttask.task.dto.TaskResponse;
import com.studenttask.task.dto.TaskSortField;
import com.studenttask.task.entity.Priority;
import com.studenttask.task.entity.TaskStatus;
import com.studenttask.task.service.TaskService;
import jakarta.validation.Valid;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    static final int DEFAULT_PAGE_SIZE = 10;
    static final int MAX_PAGE_SIZE = 50;

    private final TaskService service;

    public TaskController(TaskService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TaskResponse>> create(@Valid @RequestBody TaskRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(service.create(SecurityUtils.currentUserId(), request)));
    }

    /** deadlineFrom is inclusive, deadlineTo exclusive; both ISO-8601 with Z or an offset. */
    @GetMapping
    public ApiResponse<List<TaskResponse>> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String subject,
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) Priority priority,
            @RequestParam(required = false) String deadlineFrom,
            @RequestParam(required = false) String deadlineTo,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String dir,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size) {
        if (page < 0) {
            throw invalid("Parameter page tidak boleh negatif.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw invalid("Parameter size harus antara 1 dan " + MAX_PAGE_SIZE + ".");
        }
        TaskFilter filter = new TaskFilter(q, subject, status, priority,
                parseInstant("deadlineFrom", deadlineFrom), parseInstant("deadlineTo", deadlineTo));
        Sort order = TaskSortField.parse(sort).toSort(parseDirection(dir));
        Page<TaskResponse> result =
                service.list(SecurityUtils.currentUserId(), filter, PageRequest.of(page, size, order));
        return ApiResponse.ok(result.getContent(),
                new PageMeta(result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages()));
    }

    @GetMapping("/subjects")
    public ApiResponse<List<String>> subjects() {
        return ApiResponse.ok(service.subjects(SecurityUtils.currentUserId()));
    }

    @GetMapping("/{id}")
    public ApiResponse<TaskResponse> get(@PathVariable long id) {
        return ApiResponse.ok(service.get(SecurityUtils.currentUserId(), id));
    }

    @PutMapping("/{id}")
    public ApiResponse<TaskResponse> update(@PathVariable long id, @Valid @RequestBody TaskRequest request) {
        return ApiResponse.ok(service.update(SecurityUtils.currentUserId(), id, request));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<TaskResponse> updateStatus(@PathVariable long id, @Valid @RequestBody StatusRequest request) {
        return ApiResponse.ok(service.updateStatus(SecurityUtils.currentUserId(), id, request.status()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        service.delete(SecurityUtils.currentUserId(), id);
        return ResponseEntity.noContent().build();
    }

    private static Sort.Direction parseDirection(String dir) {
        if (dir == null || dir.equals("asc")) {
            return Sort.Direction.ASC;
        }
        if (dir.equals("desc")) {
            return Sort.Direction.DESC;
        }
        throw invalid("Parameter dir harus asc atau desc.");
    }

    private static Instant parseInstant(String name, String value) {
        if (value == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException e) {
            throw invalid("Parameter " + name + " harus berformat ISO-8601 dengan zona, mis. 2026-12-01T00:00:00Z.");
        }
    }

    private static AppException invalid(String message) {
        return new AppException(ErrorCode.VALIDATION_ERROR, message);
    }
}
