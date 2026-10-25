package com.vm325.inventory_back.services;

import com.vm325.inventory_back.entities.User;
import com.vm325.inventory_back.enums.VerificationType;
import com.vm325.inventory_back.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PasswordRecoveryServiceImplTest {

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private VerificationCodeService codes;
    private MailService mail;
    private LoginAttemptService attempts;
    private PasswordRecoveryServiceImpl service;
    private User ana;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        codes = mock(VerificationCodeService.class);
        mail = mock(MailService.class);
        attempts = mock(LoginAttemptService.class);
        service = new PasswordRecoveryServiceImpl(userRepository, passwordEncoder, codes, mail, attempts);
        ReflectionTestUtils.setField(service, "frontendUrl", "http://localhost:5173");

        ana = new User();
        ana.setName("Ana");
        ana.setUserName("ana");
        ana.setEmail("ana@test.com");
        ana.setActive(true);
        when(codes.resetTtlMinutes()).thenReturn(30);
    }

    @Test
    void unknownEmailSendsNothingAndDoesNotFail() {
        when(userRepository.findByEmailIgnoreCase("nadie@test.com")).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> service.requestReset("nadie@test.com"));

        verifyNoInteractions(mail);
        verify(codes, never()).issueResetToken(any());
    }

    @Test
    void inactiveAccountSendsNothing() {
        ana.setActive(false);
        when(userRepository.findByEmailIgnoreCase("ana@test.com")).thenReturn(Optional.of(ana));

        service.requestReset("ana@test.com");

        verifyNoInteractions(mail);
    }

    @Test
    void knownEmailGetsLinkWithTokenToTheFrontend() {
        when(userRepository.findByEmailIgnoreCase("ana@test.com")).thenReturn(Optional.of(ana));
        when(codes.issueResetToken(ana)).thenReturn("tok123");

        service.requestReset(" ana@test.com ");

        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(mail).sendPasswordReset(eq("ana@test.com"), eq("Ana"), link.capture(), eq(30));
        assertEquals("http://localhost:5173/auth/reset?token=tok123", link.getValue());
    }

    @Test
    void cooldownOrSmtpFailureIsSwallowedToAvoidRevealingAccounts() {
        when(userRepository.findByEmailIgnoreCase("ana@test.com")).thenReturn(Optional.of(ana));
        when(codes.issueResetToken(ana)).thenThrow(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "espere"));

        assertDoesNotThrow(() -> service.requestReset("ana@test.com"));

        verifyNoInteractions(mail);
    }

    @Test
    void invalidTokenIsRejectedWithoutChangingPassword() {
        when(codes.consumeResetToken("malo")).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.resetPassword("malo", "Nueva1234"));

        assertEquals(400, ex.getStatusCode().value());
        verify(userRepository, never()).save(any());
    }

    @Test
    void validTokenChangesPasswordAndInvalidatesPendingCodes() {
        when(codes.consumeResetToken("ok")).thenReturn(Optional.of(ana));
        when(passwordEncoder.encode("Nueva1234")).thenReturn("HASH");

        service.resetPassword("ok", "Nueva1234");

        assertEquals("HASH", ana.getPassword());
        verify(userRepository).save(ana);
        verify(codes).invalidate(ana, VerificationType.LOGIN_2FA);
        verify(attempts).recordSuccess("ana");
    }
}
