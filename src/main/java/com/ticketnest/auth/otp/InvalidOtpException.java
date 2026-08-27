package com.ticketnest.auth.otp;

public class InvalidOtpException extends RuntimeException {
    public InvalidOtpException() {
        super("Invalid or expired verification code");
    }
}
