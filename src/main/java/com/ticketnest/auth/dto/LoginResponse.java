package com.ticketnest.auth.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class LoginResponse {

    private String token;
    private String refreshToken;
    private UUID id;
    private String email;
    private String firstName;
    private String lastName;
    private String role;
}