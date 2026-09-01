package com.ticketnest.admin;

import com.ticketnest.admin.dto.AdminUserResponse;
import com.ticketnest.admin.dto.RoleAssignmentRequest;
import com.ticketnest.admin.dto.RoleRequest;
import com.ticketnest.admin.dto.RoleResponse;
import com.ticketnest.common.dto.PageResponse;
import com.ticketnest.entity.Permission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminRoleController {
    private final AdminRoleService service;

    @GetMapping("/permissions")
    public List<String> permissions() {
        return Arrays.stream(Permission.values()).map(Enum::name).sorted().toList();
    }

    @GetMapping("/roles")
    public List<RoleResponse> roles() { return service.getRoles(); }

    @GetMapping("/roles/{id}")
    public RoleResponse role(@PathVariable UUID id) { return service.getRole(id); }

    @PostMapping("/roles")
    public ResponseEntity<RoleResponse> create(@Valid @RequestBody RoleRequest request) {
        return ResponseEntity.status(201).body(service.createRole(request));
    }

    @PutMapping("/roles/{id}")
    public RoleResponse update(@PathVariable UUID id, @Valid @RequestBody RoleRequest request) {
        return service.updateRole(id, request);
    }

    @DeleteMapping("/roles/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.deleteRole(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/users")
    public PageResponse<AdminUserResponse> users(@PageableDefault(size = 20, sort = "email") Pageable pageable) {
        return service.getUsers(pageable);
    }

    @PutMapping("/users/{userId}/roles")
    public AdminUserResponse replaceRoles(@PathVariable UUID userId, @Valid @RequestBody RoleAssignmentRequest request) {
        return service.replaceRoles(userId, request.roleIds());
    }
}
