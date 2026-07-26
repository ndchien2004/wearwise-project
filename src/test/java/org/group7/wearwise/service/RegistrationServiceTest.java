package org.group7.wearwise.service;

import org.group7.wearwise.entity.PendingRegistration;
import org.group7.wearwise.exception.BusinessRuleException;
import org.group7.wearwise.exception.ErrorCode;
import org.group7.wearwise.repository.AppUserRepository;
import org.group7.wearwise.repository.PendingRegistrationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Hai người cùng chọn một tên đăng nhập không được để tới lúc nhập xong OTP mới báo hỏng —
 * lúc đó người dùng không còn cách nào đổi tên ngoài việc làm lại từ đầu.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RegistrationServiceTest {

    private static final String OTP = "123456";

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private PendingRegistrationRepository pendingRegistrationRepository;

    @Mock
    private SecureTokenGenerator secureTokenGenerator;

    @Mock
    private AuthMailService authMailService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthService authService;

    private RegistrationService registrationService;

    @BeforeEach
    void setUp() {
        registrationService = new RegistrationService(
                appUserRepository,
                pendingRegistrationRepository,
                secureTokenGenerator,
                authMailService,
                passwordEncoder,
                authService,
                600,
                5,
                5
        );

        when(secureTokenGenerator.hash(anyString())).thenAnswer(invocation -> "hash:" + invocation.getArgument(0));
        when(passwordEncoder.encode(anyString())).thenReturn("encoded-password");
    }

    @Test
    void requestOtpRejectsAUsernameAnotherEmailIsStillWaitingOn() {
        when(pendingRegistrationRepository
                .existsByUsernameAndEmailNotAndUsedAtIsNullAndExpiresAtAfter(
                        eq("demo"), eq("second@example.com"), any(LocalDateTime.class)))
                .thenReturn(true);

        assertThatThrownBy(() -> registrationService.requestOtp("Demo", "second@example.com", "password123"))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USERNAME_TAKEN);

        verify(authMailService, never()).sendRegistrationOtpEmail(anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    void requestOtpSendsTheCodeWhenTheUsernameIsFree() {
        registrationService.requestOtp("Demo", "demo@example.com", "password123");

        ArgumentCaptor<PendingRegistration> captor = ArgumentCaptor.forClass(PendingRegistration.class);
        verify(pendingRegistrationRepository).save(captor.capture());

        assertThat(captor.getValue().getUsername()).isEqualTo("demo");
        assertThat(captor.getValue().getEmail()).isEqualTo("demo@example.com");
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("encoded-password");
        verify(authMailService).sendRegistrationOtpEmail(anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    void verifyOtpBurnsTheRequestWhenTheUsernameWasTakenMeanwhile() {
        PendingRegistration pending = pending();
        when(pendingRegistrationRepository.findFirstByEmailAndUsedAtIsNullOrderByCreatedAtDesc("demo@example.com"))
                .thenReturn(Optional.of(pending));
        when(appUserRepository.existsByUsername("demo")).thenReturn(true);

        assertThatThrownBy(() -> registrationService.verifyOtp("demo@example.com", OTP))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USERNAME_TAKEN);

        assertThat(pending.getUsedAt()).isNotNull();
        verify(authService, never()).registerWithHashedPassword(anyString(), anyString(), anyString());
    }

    @Test
    void verifyOtpCreatesTheAccountWhenTheUsernameIsStillFree() {
        PendingRegistration pending = pending();
        when(pendingRegistrationRepository.findFirstByEmailAndUsedAtIsNullOrderByCreatedAtDesc("demo@example.com"))
                .thenReturn(Optional.of(pending));
        when(appUserRepository.existsByUsername("demo")).thenReturn(false);

        registrationService.verifyOtp("demo@example.com", OTP);

        verify(authService).registerWithHashedPassword("demo", "demo@example.com", "encoded-password");
        assertThat(pending.getUsedAt()).isNotNull();
    }

    private PendingRegistration pending() {
        return PendingRegistration.builder()
                .username("demo")
                .email("demo@example.com")
                .passwordHash("encoded-password")
                .otpHash("hash:" + OTP)
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .createdAt(LocalDateTime.now())
                .build();
    }
}
