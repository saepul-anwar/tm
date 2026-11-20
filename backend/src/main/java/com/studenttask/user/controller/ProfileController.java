package com.studenttask.user.controller;

import com.studenttask.common.api.ApiResponse;
import com.studenttask.security.SecurityUtils;
import com.studenttask.security.UserRateLimiter;
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

    static final int UPDATE_PER_MINUTE = 10;

    private final ProfileService service;
    private final UserRateLimiter rateLimiter;

    public ProfileController(ProfileService service, UserRateLimiter rateLimiter) {
        this.service = service;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping
    public ApiResponse<ProfileResponse> get() {
        return ApiResponse.ok(service.get(SecurityUtils.currentUserId()));
    }

    @PutMapping
    public ApiResponse<ProfileResponse> update(@Valid @RequestBody UpdateProfileRequest request) {
        long userId = SecurityUtils.currentUserId();
        // Profile updates can run BCrypt (current password check), so cap the per-user rate.
        rateLimiter.check("profile-update", userId, UPDATE_PER_MINUTE);
        return ApiResponse.ok(service.update(userId, request));
    }
}
