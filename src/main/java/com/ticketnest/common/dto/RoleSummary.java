package com.ticketnest.common.dto;

import com.ticketnest.entity.Role;

import java.util.UUID;

public record RoleSummary(UUID id, String name) {
    public static RoleSummary from(Role role) {
        return new RoleSummary(role.getId(), role.getName());
    }
}
