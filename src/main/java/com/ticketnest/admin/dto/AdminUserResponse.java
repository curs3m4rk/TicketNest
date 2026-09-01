package com.ticketnest.admin.dto;

import com.ticketnest.common.dto.RoleSummary;
import com.ticketnest.entity.User;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public record AdminUserResponse(UUID id, String email, String firstName, String lastName,
                                String phoneNumber, boolean active, List<RoleSummary> roles) {
    public static AdminUserResponse from(User user) {
        var roles = user.getRoles().stream()
                .sorted(Comparator.comparing(com.ticketnest.entity.Role::getName))
                .map(RoleSummary::from)
                .toList();
        return new AdminUserResponse(user.getId(), user.getEmail(), user.getFirstName(), user.getLastName(),
                user.getPhoneNumber(), user.isActive(), roles);
    }
}
