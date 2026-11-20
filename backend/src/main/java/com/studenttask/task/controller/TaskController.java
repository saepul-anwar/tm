package com.studenttask.task.controller;

import com.studenttask.common.api.ApiResponse;
import com.studenttask.security.SecurityUtils;
import com.studenttask.task.dto.StatusRequest;
import com.studenttask.task.dto.TaskRequest;
import com.studenttask.task.dto.TaskResponse;
import com.studenttask.task.service.TaskService;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService service;

    public TaskController(TaskService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TaskResponse>> create(@Valid @RequestBody TaskRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(service.create(SecurityUtils.currentUserId(), request)));
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
}
