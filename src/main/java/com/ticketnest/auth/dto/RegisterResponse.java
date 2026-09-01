package com.ticketnest.auth.dto;

import com.ticketnest.common.dto.RoleSummary;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;
import java.util.List;

/**
 * Registration output. Excludes password hash; returns user identity and default role.
 */
@Getter
@Setter
public class RegisterResponse {

    private UUID id;
    private String email;
    private String firstName;
    private String lastName;
    private String phoneNumber;
    private List<RoleSummary> roles;
}