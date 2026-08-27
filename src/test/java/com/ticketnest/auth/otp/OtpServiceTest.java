package com.ticketnest.auth.otp;

import com.ticketnest.auth.AuthService;
import com.ticketnest.auth.dto.LoginResponse;
import com.ticketnest.auth.dto.OtpRequest;
import com.ticketnest.auth.dto.OtpVerifyRequest;
import com.ticketnest.entity.OtpChallenge;
import com.ticketnest.entity.OtpChannel;
import com.ticketnest.entity.User;
import com.ticketnest.repository.OtpChallengeRepository;
import com.ticketnest.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OtpServiceTest {

    @Mock
    private OtpChallengeRepository challengeRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AuthService authService;

    private CapturingSender sender;
    private OtpService service;

    @BeforeEach
    void setUp() {
        sender = new CapturingSender();
        service = new OtpService(challengeRepository, userRepository, authService, java.util.List.of(sender));
        ReflectionTestUtils.setField(service, "otpSecret", "a-test-secret-that-is-not-used-in-production");
        ReflectionTestUtils.setField(service, "ttlSeconds", 300L);
        ReflectionTestUtils.setField(service, "resendCooldownSeconds", 60L);
        ReflectionTestUtils.setField(service, "maxRequestsPerWindow", 5L);
        ReflectionTestUtils.setField(service, "requestWindowSeconds", 900L);
        ReflectionTestUtils.setField(service, "maxVerificationAttempts", 5);
        when(challengeRepository.findTopByDestinationOrderByCreatedAtDesc(any())).thenReturn(Optional.empty());
    }

    @Test
    void requestNormalizesEmailAndSendsSixDigitCode() {
        OtpRequest request = new OtpRequest();
        request.setChannel(OtpChannel.EMAIL);
        request.setIdentifier("  Person@Example.COM ");

        var response = service.request(request);

        ArgumentCaptor<OtpChallenge> saved = ArgumentCaptor.forClass(OtpChallenge.class);
        verify(challengeRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getDestination()).isEqualTo("person@example.com");
        assertThat(saved.getValue().getCodeHash()).doesNotContain(sender.code);
        assertThat(sender.destination).isEqualTo("person@example.com");
        assertThat(sender.code).matches("\\d{6}");
        assertThat(response.getChallengeId()).isEqualTo(saved.getValue().getId());
    }

    @Test
    void verifyConsumesChallengeCreatesUserAndReturnsLoginResponse() {
        OtpRequest request = new OtpRequest();
        request.setChannel(OtpChannel.EMAIL);
        request.setIdentifier("new@example.com");
        var requested = service.request(request);

        ArgumentCaptor<OtpChallenge> saved = ArgumentCaptor.forClass(OtpChallenge.class);
        verify(challengeRepository).saveAndFlush(saved.capture());
        OtpChallenge challenge = saved.getValue();
        when(challengeRepository.findByIdForUpdate(requested.getChallengeId())).thenReturn(Optional.of(challenge));
        when(userRepository.findByEmailIgnoreCase("new@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(UUID.randomUUID());
            return user;
        });
        LoginResponse expected = new LoginResponse();
        when(authService.issueLoginTokens(any(User.class))).thenReturn(expected);

        OtpVerifyRequest verifyRequest = new OtpVerifyRequest();
        verifyRequest.setChallengeId(requested.getChallengeId());
        verifyRequest.setCode(sender.code);
        LoginResponse actual = service.verify(verifyRequest);

        assertThat(actual).isSameAs(expected);
        assertThat(challenge.getConsumedAt()).isNotNull();
        assertThat(challenge.getAttemptCount()).isEqualTo(1);
    }

    private static class CapturingSender implements OtpSender {
        private String destination;
        private String code;

        @Override
        public OtpChannel channel() {
            return OtpChannel.EMAIL;
        }

        @Override
        public void send(String destination, String code, Duration validity) {
            this.destination = destination;
            this.code = code;
        }
    }
}
