package com.studenttask.user.controller;

import com.studenttask.common.api.ApiResponse;
import com.studenttask.security.SecurityUtils;
import com.studenttask.user.dto.ProfileResponse;
import com.studenttask.user.dto.UpdateProfileRequest;
import com.studenttask.user.service.ProfileService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final ProfileService service;

    public ProfileController(ProfileService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<ProfileResponse> get() {
        return ApiResponse.ok(service.get(SecurityUtils.currentUserId()));
    }

    @PutMapping
    public ApiResponse<ProfileResponse> update(@Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.ok(service.update(SecurityUtils.currentUserId(), request));
    }
}
