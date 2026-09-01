package com.ticketnest.admin;

import com.jayway.jsonpath.JsonPath;
import com.ticketnest.auth.BaseIntegrationTest;
import com.ticketnest.auth.JwtUtil;
import com.ticketnest.entity.Role;
import com.ticketnest.entity.User;
import com.ticketnest.repository.RoleRepository;
import com.ticketnest.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AdminRoleIntegrationTest extends BaseIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtUtil jwtUtil;

    private String adminToken;
    private String userToken;
    private User user;
    private Role userRole;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        userRole = roleRepository.findByName(Role.USER).orElseThrow();
        Role adminRole = roleRepository.findByName(Role.ADMIN).orElseThrow();
        User admin = createUser("rbac-admin-" + UUID.randomUUID() + "@example.com", "+1556" + randomDigits(), userRole);
        admin.getRoles().add(adminRole);
        userRepository.save(admin);
        adminToken = jwtUtil.generateToken(admin.getEmail());

        user = createUser("rbac-user-" + UUID.randomUUID() + "@example.com", "+1557" + randomDigits(), userRole);
        userToken = jwtUtil.generateToken(user.getEmail());
    }

    @Test
    void userCannotAccessRoleAdministration() throws Exception {
        mockMvc.perform(get("/api/admin/roles").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    @Test
    void adminCanCreateAssignAndManageCustomRole() throws Exception {
        String roleName = "EVENT_MANAGER_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        String created = mockMvc.perform(post("/api/admin/roles")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","description":"Manages events","permissions":["VENUE_MANAGE","SHOW_MANAGE"]}
                                """.formatted(roleName)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.permissions", hasItem("VENUE_MANAGE")))
                .andReturn().getResponse().getContentAsString();
        UUID roleId = UUID.fromString(JsonPath.read(created, "$.id"));

        mockMvc.perform(put("/api/admin/users/{id}/roles", user.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roleIds":["%s","%s"]}
                                """.formatted(userRole.getId(), roleId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles.length()").value(2))
                .andExpect(jsonPath("$.roles[*].name", hasItem(roleName)));

        mockMvc.perform(delete("/api/admin/roles/{id}", roleId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());

        mockMvc.perform(put("/api/admin/roles/{id}", userRole.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"USER","description":"Changed","permissions":[]}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void lastActiveAdminCannotLoseAdminRole() throws Exception {
        var admin = userRepository.findWithRolesByEmail(jwtUtil.getEmail(adminToken)).orElseThrow();
        mockMvc.perform(put("/api/admin/users/{id}/roles", admin.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleIds\":[\"" + userRole.getId() + "\"]}"))
                .andExpect(status().isConflict());
    }

    @Test
    void permissionChangesApplyImmediatelyToExistingToken() throws Exception {
        String roleName = "VENUE_MANAGER_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        String created = mockMvc.perform(post("/api/admin/roles")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","description":null,"permissions":["VENUE_MANAGE"]}
                                """.formatted(roleName)))
                .andReturn().getResponse().getContentAsString();
        UUID roleId = UUID.fromString(JsonPath.read(created, "$.id"));

        replaceUserRoles(userRole.getId(), roleId);
        mockMvc.perform(post("/api/venues")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"RBAC Arena","city":"Pune","address":"10 Access Road"}
                                """))
                .andExpect(status().isCreated());

        replaceUserRoles(userRole.getId());
        mockMvc.perform(post("/api/venues")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Denied Arena","city":"Pune","address":"11 Access Road"}
                                """))
                .andExpect(status().isForbidden());
    }

    private void replaceUserRoles(UUID... ids) throws Exception {
        String roleIds = java.util.Arrays.stream(ids).map(id -> "\"" + id + "\"")
                .collect(java.util.stream.Collectors.joining(","));
        mockMvc.perform(put("/api/admin/users/{id}/roles", user.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleIds\":[" + roleIds + "]}"))
                .andExpect(status().isOk());
    }

    private User createUser(String email, String phone, Role role) {
        User created = new User();
        created.setEmail(email);
        created.setPasswordHash(passwordEncoder.encode("Password123"));
        created.setFirstName("RBAC");
        created.setLastName("Tester");
        created.setPhoneNumber(phone);
        created.setActive(true);
        created.setCreatedAt(Instant.now());
        created.getRoles().add(role);
        return userRepository.save(created);
    }

    private String randomDigits() {
        return String.valueOf(Math.abs(UUID.randomUUID().getLeastSignificantBits() % 1_000_000_000L));
    }
}
