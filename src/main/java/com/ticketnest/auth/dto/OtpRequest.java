package com.ticketnest.auth.dto;

import com.ticketnest.entity.OtpChannel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OtpRequest {

    @NotBlank(message = "Identifier is required")
    @Size(max = 254, message = "Identifier is too long")
    private String identifier;

    @NotNull(message = "Channel is required")
    private OtpChannel channel;
}
