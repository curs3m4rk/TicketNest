package com.ticketnest.admin;

import com.ticketnest.admin.dto.AdminUserResponse;
import com.ticketnest.admin.dto.RoleRequest;
import com.ticketnest.admin.dto.RoleResponse;
import com.ticketnest.common.ConflictException;
import com.ticketnest.common.dto.PageResponse;
import com.ticketnest.entity.Role;
import com.ticketnest.entity.User;
import com.ticketnest.repository.RoleRepository;
import com.ticketnest.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminRoleService {
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<RoleResponse> getRoles() {
        return roleRepository.findAll(Sort.by("name")).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public RoleResponse getRole(UUID id) {
        return toResponse(requireRole(id));
    }

    @Transactional
    public RoleResponse createRole(RoleRequest request) {
        if (roleRepository.existsByName(request.name())) {
            throw new ConflictException("Role name already exists");
        }
        Role role = new Role();
        role.setName(request.name());
        role.setDescription(request.description());
        role.setSystemRole(false);
        role.setPermissions(new HashSet<>(request.permissions()));
        return toResponse(roleRepository.save(role));
    }

    @Transactional
    public RoleResponse updateRole(UUID id, RoleRequest request) {
        Role role = requireRole(id);
        assertCustom(role);
        roleRepository.findByName(request.name())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> { throw new ConflictException("Role name already exists"); });
        role.setName(request.name());
        role.setDescription(request.description());
        role.setPermissions(new HashSet<>(request.permissions()));
        return toResponse(roleRepository.save(role));
    }

    @Transactional
    public void deleteRole(UUID id) {
        Role role = requireRole(id);
        assertCustom(role);
        if (userRepository.countByRoles_Id(id) > 0) {
            throw new ConflictException("Assigned roles cannot be deleted");
        }
        roleRepository.delete(role);
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminUserResponse> getUsers(Pageable pageable) {
        var page = userRepository.findAll(pageable).map(AdminUserResponse::from);
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages(), page.isFirst(), page.isLast(), page.isEmpty());
    }

    @Transactional
    public AdminUserResponse replaceRoles(UUID userId, Set<UUID> roleIds) {
        User user = userRepository.findWithRolesById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
        List<Role> roles = roleRepository.findAllById(roleIds);
        if (roles.size() != roleIds.size()) {
            throw new EntityNotFoundException("One or more roles were not found");
        }
        boolean removingAdmin = user.isActive()
                && user.getRoles().stream().anyMatch(role -> Role.ADMIN.equals(role.getName()))
                && roles.stream().noneMatch(role -> Role.ADMIN.equals(role.getName()));
        if (removingAdmin) {
            roleRepository.findForUpdateByName(Role.ADMIN)
                    .orElseThrow(() -> new IllegalStateException("System ADMIN role is missing"));
        }
        if (removingAdmin && userRepository.countByActiveTrueAndRoles_Name(Role.ADMIN) <= 1) {
            throw new ConflictException("The last active ADMIN cannot be removed");
        }
        user.getRoles().clear();
        user.getRoles().addAll(roles);
        return AdminUserResponse.from(userRepository.save(user));
    }

    private Role requireRole(UUID id) {
        return roleRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Role not found"));
    }

    private void assertCustom(Role role) {
        if (role.isSystemRole()) {
            throw new ConflictException("System roles cannot be modified or deleted");
        }
    }

    private RoleResponse toResponse(Role role) {
        return RoleResponse.from(role, userRepository.countByRoles_Id(role.getId()));
    }
}
