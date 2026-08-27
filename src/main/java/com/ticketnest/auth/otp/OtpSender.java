package com.ticketnest.auth.otp;

import com.ticketnest.entity.OtpChannel;

import java.time.Duration;

public interface OtpSender {
    OtpChannel channel();
    void send(String destination, String code, Duration validity);
}
