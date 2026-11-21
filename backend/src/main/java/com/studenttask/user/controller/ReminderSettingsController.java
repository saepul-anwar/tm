package com.studenttask.user.controller;

import com.studenttask.common.api.ApiResponse;
import com.studenttask.security.SecurityUtils;
import com.studenttask.user.dto.ReminderSettingsRequest;
import com.studenttask.user.dto.ReminderSettingsResponse;
import com.studenttask.user.service.ReminderSettingsService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reminders/settings")
public class ReminderSettingsController {

    private final ReminderSettingsService service;

    public ReminderSettingsController(ReminderSettingsService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<ReminderSettingsResponse> get() {
        return ApiResponse.ok(service.get(SecurityUtils.currentUserId()));
    }

    @PutMapping
    public ApiResponse<ReminderSettingsResponse> update(@Valid @RequestBody ReminderSettingsRequest request) {
        return ApiResponse.ok(service.update(SecurityUtils.currentUserId(), request));
    }
}
