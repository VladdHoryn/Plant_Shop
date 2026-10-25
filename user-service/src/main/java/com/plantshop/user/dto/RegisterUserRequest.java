package com.plantshop.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterUserRequest(
        @NotBlank @Size(max = 150) String fullName,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Pattern(regexp = "\\+[1-9][0-9 ()-]{7,20}", message = "must be in international format, e.g. +44 7700 900123")
        String phone,
        // BCrypt only uses the first 72 bytes
        @NotBlank @Size(min = 10, max = 72) String password
) {
}
