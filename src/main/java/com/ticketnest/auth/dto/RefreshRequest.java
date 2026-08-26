package com.ticketnest.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RefreshRequest {

    @NotBlank(message = "Refresh token is required")
    @Size(max = 512, message = "Refresh token must be at most 512 characters")
    private String refreshToken;
}
