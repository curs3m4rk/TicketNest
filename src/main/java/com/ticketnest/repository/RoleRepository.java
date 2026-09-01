package com.ticketnest.repository;

import com.ticketnest.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.UUID;

public interface RoleRepository extends JpaRepository<Role, UUID> {
    Optional<Role> findByName(String name);
    boolean existsByName(String name);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Role> findForUpdateByName(String name);
}
