package com.ticketnest.admin.dto;

import com.ticketnest.entity.Permission;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record RoleRequest(
        @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]{2,49}$", message = "Role name must be an uppercase identifier") String name,
        @Size(max = 255) String description,
        @NotNull Set<Permission> permissions
) {}
