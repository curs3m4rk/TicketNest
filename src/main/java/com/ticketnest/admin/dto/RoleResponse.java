package com.ticketnest.admin.dto;

import com.ticketnest.entity.Permission;
import com.ticketnest.entity.Role;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public record RoleResponse(UUID id, String name, String description, boolean systemRole,
                           List<Permission> permissions, long assignmentCount) {
    public static RoleResponse from(Role role, long assignmentCount) {
        var permissions = role.getPermissions().stream().sorted(Comparator.comparing(Enum::name)).toList();
        return new RoleResponse(role.getId(), role.getName(), role.getDescription(), role.isSystemRole(), permissions, assignmentCount);
    }
}
