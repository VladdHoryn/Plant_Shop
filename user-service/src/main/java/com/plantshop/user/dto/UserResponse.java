package com.plantshop.user.dto;

import java.time.Instant;

public record UserResponse(Long id, String fullName, String email, String phone, Instant createdAt) {
}
