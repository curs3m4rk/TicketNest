package com.ticketnest.auth.dto;

import com.ticketnest.common.dto.RoleSummary;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;
import java.util.List;

@Getter
@Setter
public class LoginResponse {

    private String token;
    private String refreshToken;
    private UUID id;
    private String email;
    private String firstName;
    private String lastName;
    private String phoneNumber;
    private List<RoleSummary> roles;
}