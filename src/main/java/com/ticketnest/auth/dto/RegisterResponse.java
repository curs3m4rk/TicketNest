package com.ticketnest.auth.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

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
    private String role;
}