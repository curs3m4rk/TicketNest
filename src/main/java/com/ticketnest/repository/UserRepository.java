package com.ticketnest.repository;

import com.ticketnest.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * User data access. Provides email-based lookups for authentication.
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);  // used by authentication
    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    Optional<User> findWithRolesByEmail(String email);
    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    Optional<User> findWithRolesById(UUID id);
    boolean existsByEmail(String email);       // used by registration
    boolean existsByPhoneNumber(String phoneNumber);
    long countByRoles_Id(UUID roleId);
    long countByActiveTrueAndRoles_Name(String roleName);
}