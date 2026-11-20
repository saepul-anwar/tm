package com.studenttask.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "Email wajib diisi.") @Size(max = 254, message = "Email terlalu panjang.") String email,
        @NotBlank(message = "Kata sandi wajib diisi.") @Size(max = 1000, message = "Kata sandi terlalu panjang.")
                String password) {

    @Override
    public String toString() {
        return "LoginRequest[redacted]";
    }
}
