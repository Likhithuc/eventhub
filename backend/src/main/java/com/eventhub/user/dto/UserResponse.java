package com.eventhub.user.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.eventhub.user.Role;
import com.eventhub.user.User;
import com.eventhub.user.UserStatus;

public record UserResponse(UUID id, String name, String email, List<Role> roles, UserStatus status, Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRoles().stream().sorted().toList(),
                user.getStatus(),
                user.getCreatedAt());
    }
}
