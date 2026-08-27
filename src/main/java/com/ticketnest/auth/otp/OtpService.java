package com.ticketnest.auth.otp;

import com.ticketnest.auth.AuthService;
import com.ticketnest.auth.dto.LoginResponse;
import com.ticketnest.auth.dto.OtpRequest;
import com.ticketnest.auth.dto.OtpRequestResponse;
import com.ticketnest.auth.dto.OtpVerifyRequest;
import com.ticketnest.entity.OtpChallenge;
import com.ticketnest.entity.OtpChannel;
import com.ticketnest.entity.Role;
import com.ticketnest.entity.User;
import com.ticketnest.repository.OtpChallengeRepository;
import com.ticketnest.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class OtpService {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern E164_PHONE = Pattern.compile("^\\+[1-9]\\d{7,14}$");
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final OtpChallengeRepository challengeRepository;
    private final UserRepository userRepository;
    private final AuthService authService;
    private final List<OtpSender> senders;

    @Value("${app.otp.secret}")
    private String otpSecret;

    @Value("${app.otp.ttl-seconds}")
    private long ttlSeconds;

    @Value("${app.otp.resend-cooldown-seconds}")
    private long resendCooldownSeconds;

    @Value("${app.otp.max-requests-per-window}")
    private long maxRequestsPerWindow;

    @Value("${app.otp.request-window-seconds}")
    private long requestWindowSeconds;

    @Value("${app.otp.max-verification-attempts}")
    private int maxVerificationAttempts;

    public OtpRequestResponse request(OtpRequest request) {
        String destination = normalize(request.getIdentifier(), request.getChannel());
        Instant now = Instant.now();

        challengeRepository.findTopByDestinationOrderByCreatedAtDesc(destination)
                .filter(latest -> latest.getCreatedAt().isAfter(now.minusSeconds(resendCooldownSeconds)))
                .ifPresent(latest -> {
                    throw new OtpRateLimitException("Please wait before requesting another code");
                });

        long recentRequests = challengeRepository.countByDestinationAndCreatedAtAfter(
                destination, now.minusSeconds(requestWindowSeconds));
        if (recentRequests >= maxRequestsPerWindow) {
            throw new OtpRateLimitException("Too many verification requests. Try again later");
        }

        OtpSender sender = senders.stream()
                .filter(candidate -> candidate.channel() == request.getChannel())
                .findFirst()
                .orElseThrow(() -> new OtpDeliveryException(request.getChannel() + " OTP delivery is not configured"));

        String code = "%06d".formatted(SECURE_RANDOM.nextInt(1_000_000));
        OtpChallenge challenge = new OtpChallenge();
        challenge.setId(UUID.randomUUID());
        challenge.setDestination(destination);
        challenge.setChannel(request.getChannel());
        challenge.setCodeHash(hashCode(challenge.getId(), code));
        challenge.setCreatedAt(now);
        challenge.setExpiresAt(now.plusSeconds(ttlSeconds));
        challenge.setMaxAttempts(maxVerificationAttempts);
        challenge.setAttemptCount(0);
        challengeRepository.saveAndFlush(challenge);

        try {
            sender.send(destination, code, Duration.ofSeconds(ttlSeconds));
        } catch (RuntimeException e) {
            challengeRepository.deleteById(challenge.getId());
            throw new OtpDeliveryException("Unable to deliver verification code", e);
        }

        return new OtpRequestResponse(challenge.getId(), challenge.getExpiresAt());
    }

    @Transactional(noRollbackFor = InvalidOtpException.class)
    public LoginResponse verify(OtpVerifyRequest request) {
        OtpChallenge challenge = challengeRepository.findByIdForUpdate(request.getChallengeId())
                .orElseThrow(InvalidOtpException::new);
        Instant now = Instant.now();

        if (challenge.getConsumedAt() != null
                || !challenge.getExpiresAt().isAfter(now)
                || challenge.getAttemptCount() >= challenge.getMaxAttempts()) {
            throw new InvalidOtpException();
        }

        challenge.setAttemptCount(challenge.getAttemptCount() + 1);
        if (!constantTimeEquals(challenge.getCodeHash(), hashCode(challenge.getId(), request.getCode()))) {
            challengeRepository.save(challenge);
            throw new InvalidOtpException();
        }

        challenge.setConsumedAt(now);
        challengeRepository.save(challenge);
        User user = resolveOrCreateUser(challenge, now);
        return authService.issueLoginTokens(user);
    }

    private User resolveOrCreateUser(OtpChallenge challenge, Instant verifiedAt) {
        User user;
        if (challenge.getChannel() == OtpChannel.EMAIL) {
            user = userRepository.findByEmailIgnoreCase(challenge.getDestination()).orElseGet(() -> {
                User created = newUser();
                created.setEmail(challenge.getDestination());
                return created;
            });
            user.setEmailVerifiedAt(verifiedAt);
        } else {
            user = userRepository.findByPhoneNumber(challenge.getDestination()).orElseGet(() -> {
                User created = newUser();
                created.setPhoneNumber(challenge.getDestination());
                return created;
            });
            user.setPhoneVerifiedAt(verifiedAt);
        }
        if (!user.isActive()) {
            throw new InvalidOtpException();
        }
        return userRepository.save(user);
    }

    private User newUser() {
        User user = new User();
        user.setRole(Role.USER);
        user.setActive(true);
        user.setCreatedAt(Instant.now());
        return user;
    }

    private String normalize(String rawIdentifier, OtpChannel channel) {
        String value = rawIdentifier.trim();
        if (channel == OtpChannel.EMAIL) {
            value = value.toLowerCase(Locale.ROOT);
            if (value.length() > 254 || !EMAIL.matcher(value).matches()) {
                throw new IllegalArgumentException("A valid email address is required");
            }
        } else if (!E164_PHONE.matcher(value).matches()) {
            throw new IllegalArgumentException("Phone number must use E.164 format, for example +919876543210");
        }
        return value;
    }

    private String hashCode(UUID challengeId, String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(otpSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal((challengeId + ":" + code).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to hash OTP", e);
        }
    }

    private boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }
}
