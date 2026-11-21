package com.studenttask.task.controller;

import com.studenttask.common.api.ApiResponse;
import com.studenttask.security.SecurityUtils;
import com.studenttask.task.dto.SummaryResponse;
import com.studenttask.task.dto.TaskResponse;
import com.studenttask.task.service.DashboardService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    public ApiResponse<SummaryResponse> summary() {
        return ApiResponse.ok(service.summary(SecurityUtils.currentUserId()));
    }

    @GetMapping("/upcoming")
    public ApiResponse<List<TaskResponse>> upcoming() {
        return ApiResponse.ok(service.upcoming(SecurityUtils.currentUserId()));
    }

    @GetMapping("/overdue")
    public ApiResponse<List<TaskResponse>> overdue() {
        return ApiResponse.ok(service.overdue(SecurityUtils.currentUserId()));
    }
}
