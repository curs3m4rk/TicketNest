package com.ticketnest.auth.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LogoutRequest {

    private String refreshToken; // optional: if omitted, revokes all user tokens
}