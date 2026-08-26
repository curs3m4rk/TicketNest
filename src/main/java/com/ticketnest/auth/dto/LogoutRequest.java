package com.ticketnest.auth.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LogoutRequest {

    @Size(min = 1, max = 512, message = "Refresh token must be between 1 and 512 characters")
    private String refreshToken;
}
