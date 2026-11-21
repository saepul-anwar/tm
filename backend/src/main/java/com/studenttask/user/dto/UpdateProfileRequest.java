package com.studenttask.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/** Partial update: a null component means "leave unchanged". Unknown JSON properties are ignored. */
public record UpdateProfileRequest(
        String fullName,
        @Size(max = 254, message = "Email maksimal 254 karakter.") @Email(message = "Format email tidak valid.")
                String email,
        @Size(max = 64, message = "Zona waktu maksimal 64 karakter.") String timezone,
        String currentPassword,
        String newPassword) {

    @Override
    public String toString() {
        return "UpdateProfileRequest[redacted]";
    }
}
