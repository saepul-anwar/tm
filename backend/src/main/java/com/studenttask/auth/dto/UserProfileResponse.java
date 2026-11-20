package com.studenttask.auth.dto;

public record UserProfileResponse(long id, String fullName, String email, String timezone) {
}
