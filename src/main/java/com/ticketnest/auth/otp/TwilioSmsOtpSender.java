package com.ticketnest.auth.otp;

import com.ticketnest.entity.OtpChannel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

@Component
@ConditionalOnProperty(name = "app.otp.sms.enabled", havingValue = "true")
public class TwilioSmsOtpSender implements OtpSender {

    private final String accountSid;
    private final String authToken;
    private final String fromNumber;
    private final HttpClient httpClient;

    public TwilioSmsOtpSender(
            @Value("${app.otp.sms.account-sid}") String accountSid,
            @Value("${app.otp.sms.auth-token}") String authToken,
            @Value("${app.otp.sms.from-number}") String fromNumber) {
        this.accountSid = requireConfigured(accountSid, "TWILIO_ACCOUNT_SID");
        this.authToken = requireConfigured(authToken, "TWILIO_AUTH_TOKEN");
        this.fromNumber = requireConfigured(fromNumber, "TWILIO_FROM_NUMBER");
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    @Override
    public OtpChannel channel() {
        return OtpChannel.SMS;
    }

    @Override
    public void send(String destination, String code, Duration validity) {
        String body = form("To", destination)
                + "&" + form("From", fromNumber)
                + "&" + form("Body", "Your TicketNest code is " + code
                + ". It expires in " + validity.toMinutes() + " minutes.");
        String basicAuth = Base64.getEncoder().encodeToString(
                (accountSid + ":" + authToken).getBytes(StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.twilio.com/2010-04-01/Accounts/" + accountSid + "/Messages.json"))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Basic " + basicAuth)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        try {
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new OtpDeliveryException("SMS provider rejected the message");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OtpDeliveryException("SMS delivery was interrupted", e);
        } catch (IOException e) {
            throw new OtpDeliveryException("SMS provider is unavailable", e);
        }
    }

    private static String form(String name, String value) {
        return URLEncoder.encode(name, StandardCharsets.UTF_8) + "="
                + URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String requireConfigured(String value, String environmentVariable) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(environmentVariable + " must be configured when SMS OTP is enabled");
        }
        return value;
    }
}
