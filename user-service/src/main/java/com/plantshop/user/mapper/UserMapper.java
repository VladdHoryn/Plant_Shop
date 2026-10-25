package com.plantshop.user.mapper;

import com.plantshop.user.dto.UserResponse;
import com.plantshop.user.entity.User;

public final class UserMapper {

    private UserMapper() {
    }

    public static UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getFullName(), user.getEmail(), user.getPhone(), user.getCreatedAt());
    }
}
