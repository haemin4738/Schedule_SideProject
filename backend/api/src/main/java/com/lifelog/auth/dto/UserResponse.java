package com.lifelog.auth.dto;

import com.lifelog.domain.user.User;
import java.time.LocalDateTime;

public record UserResponse(Long id, String email, String name, LocalDateTime createdAt) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getName(), user.getCreatedAt());
    }
}
