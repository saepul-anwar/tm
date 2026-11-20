package com.studenttask.auth.dto;

import com.studenttask.auth.validation.PasswordsMatch;
import com.studenttask.auth.validation.StrongPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@PasswordsMatch
public record RegisterRequest(
        @NotBlank(message = "Nama lengkap wajib diisi.") @Size(max = 100, message = "Nama lengkap maksimal 100 karakter.")
                String fullName,
        @NotBlank(message = "Email wajib diisi.")
                @Size(max = 254, message = "Email maksimal 254 karakter.")
                @Email(message = "Format email tidak valid.")
                String email,
        @StrongPassword String password,
        String confirmPassword) {

    @Override
    public String toString() {
        return "RegisterRequest[redacted]";
    }
}
