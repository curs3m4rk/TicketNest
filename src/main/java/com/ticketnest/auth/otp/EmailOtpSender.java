package com.ticketnest.auth.otp;

import com.ticketnest.entity.OtpChannel;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.otp.email.enabled", havingValue = "true")
public class EmailOtpSender implements OtpSender {

    private final JavaMailSender mailSender;

    @Value("${app.otp.email.from}")
    private String from;

    @Override
    public OtpChannel channel() {
        return OtpChannel.EMAIL;
    }

    @Override
    public void send(String destination, String code, Duration validity) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(destination);
        message.setSubject("Your TicketNest login code");
        message.setText("Your TicketNest login code is " + code
                + ". It expires in " + validity.toMinutes() + " minutes. Never share this code.");
        mailSender.send(message);
    }
}
