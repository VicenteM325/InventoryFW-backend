package com.vm325.inventory_back.services;

import com.vm325.inventory_back.entities.User;
import com.vm325.inventory_back.entities.VerificationCode;
import com.vm325.inventory_back.enums.VerificationType;
import com.vm325.inventory_back.repositories.VerificationCodeRepository;
import com.vm325.inventory_back.services.VerificationCodeService.Result;
import com.vm325.inventory_back.support.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VerificationCodeServiceTest {

    private final List<VerificationCode> store = new ArrayList<>();
    private MutableClock clock;
    private VerificationCodeService service;
    private User user;

    @BeforeEach
    void setUp() {
        VerificationCodeRepository repo = mock(VerificationCodeRepository.class);
        when(repo.save(any(VerificationCode.class))).thenAnswer(inv -> {
            VerificationCode v = inv.getArgument(0);
            if (!store.contains(v)) {
                store.add(v);
            }
            return v;
        });
        when(repo.findFirstByUserAndTypeAndConsumedFalseOrderByCreatedAtDesc(any(), any())).thenAnswer(inv ->
                latest(inv.getArgument(0), inv.getArgument(1), true));
        when(repo.findFirstByUserAndTypeOrderByCreatedAtDesc(any(), any())).thenAnswer(inv ->
                latest(inv.getArgument(0), inv.getArgument(1), false));
        when(repo.findFirstByCodeHashAndTypeAndConsumedFalse(anyString(), any())).thenAnswer(inv ->
                store.stream().filter(v -> v.getCodeHash().equals(inv.getArgument(0))
                        && v.getType() == inv.getArgument(1) && !v.isConsumed()).findFirst());
        when(repo.consumeActive(any(), any())).thenAnswer(inv -> {
            store.stream().filter(v -> v.getUser() == inv.getArgument(0) && v.getType() == inv.getArgument(1))
                    .forEach(v -> v.setConsumed(true));
            return 0;
        });

        clock = new MutableClock();
        service = new VerificationCodeService(repo, clock);
        ReflectionTestUtils.setField(service, "hmacSecret", "0123456789abcdef0123456789abcdef");
        ReflectionTestUtils.setField(service, "otpTtlMinutes", 10);
        ReflectionTestUtils.setField(service, "maxAttempts", 5);
        ReflectionTestUtils.setField(service, "cooldownSeconds", 60);
        ReflectionTestUtils.setField(service, "resetTtlMinutes", 30);

        user = new User();
        user.setUserId(UUID.randomUUID());
    }

    private Optional<VerificationCode> latest(User u, VerificationType t, boolean onlyActive) {
        return store.stream()
                .filter(v -> v.getUser() == u && v.getType() == t && (!onlyActive || !v.isConsumed()))
                .max(Comparator.comparing(VerificationCode::getCreatedAt));
    }

    @Test
    void otpHas6DigitsAndIsStoredHashedNotInClear() {
        String code = service.issueOtp(user, VerificationType.LOGIN_2FA);

        assertTrue(code.matches("\\d{6}"));
        assertEquals(1, store.size());
        assertNotEquals(code, store.get(0).getCodeHash());
        assertEquals(64, store.get(0).getCodeHash().length());
    }

    @Test
    void correctCodeIsValidOnlyOnce() {
        String code = service.issueOtp(user, VerificationType.LOGIN_2FA);

        assertEquals(Result.VALID, service.verifyOtp(user, VerificationType.LOGIN_2FA, code));
        assertEquals(Result.INVALID, service.verifyOtp(user, VerificationType.LOGIN_2FA, code));
    }

    @Test
    void codeIsBoundToUserAndType() {
        String code = service.issueOtp(user, VerificationType.LOGIN_2FA);
        User other = new User();
        other.setUserId(UUID.randomUUID());

        assertEquals(Result.INVALID, service.verifyOtp(other, VerificationType.LOGIN_2FA, code));
        assertEquals(Result.INVALID, service.verifyOtp(user, VerificationType.ENABLE_2FA, code));
    }

    @Test
    void wrongCodeIsInvalidAndCorrectOneStillWorksBeforeLimit() {
        String code = service.issueOtp(user, VerificationType.LOGIN_2FA);
        String wrong = code.equals("000000") ? "111111" : "000000";

        assertEquals(Result.INVALID, service.verifyOtp(user, VerificationType.LOGIN_2FA, wrong));
        assertEquals(Result.VALID, service.verifyOtp(user, VerificationType.LOGIN_2FA, code));
    }

    @Test
    void fifthWrongAttemptLocksAndKillsTheCode() {
        String code = service.issueOtp(user, VerificationType.LOGIN_2FA);
        String wrong = code.equals("000000") ? "111111" : "000000";

        for (int i = 0; i < 4; i++) {
            assertEquals(Result.INVALID, service.verifyOtp(user, VerificationType.LOGIN_2FA, wrong));
        }
        assertEquals(Result.LOCKED, service.verifyOtp(user, VerificationType.LOGIN_2FA, wrong));
        // Ni siquiera el código correcto sirve ya: hay que pedir uno nuevo.
        assertEquals(Result.INVALID, service.verifyOtp(user, VerificationType.LOGIN_2FA, code));
    }

    @Test
    void expiredCodeIsRejected() {
        String code = service.issueOtp(user, VerificationType.LOGIN_2FA);

        clock.advance(Duration.ofMinutes(10));

        assertEquals(Result.INVALID, service.verifyOtp(user, VerificationType.LOGIN_2FA, code));
    }

    @Test
    void resendWithinCooldownIsRejectedWith429() {
        service.issueOtp(user, VerificationType.LOGIN_2FA);
        clock.advance(Duration.ofSeconds(30));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.issueOtp(user, VerificationType.LOGIN_2FA));
        assertEquals(429, ex.getStatusCode().value());
    }

    @Test
    void newCodeAfterCooldownReplacesThePreviousOne() {
        String first = service.issueOtp(user, VerificationType.LOGIN_2FA);
        clock.advance(Duration.ofSeconds(61));
        String second = service.issueOtp(user, VerificationType.LOGIN_2FA);

        if (!first.equals(second)) {
            assertEquals(Result.INVALID, service.verifyOtp(user, VerificationType.LOGIN_2FA, first));
        }
        assertEquals(Result.VALID, service.verifyOtp(user, VerificationType.LOGIN_2FA, second));
    }

    @Test
    void usedCodeDoesNotBlockImmediateNewLogin() {
        String code = service.issueOtp(user, VerificationType.LOGIN_2FA);
        service.verifyOtp(user, VerificationType.LOGIN_2FA, code);

        assertDoesNotThrow(() -> service.issueOtp(user, VerificationType.LOGIN_2FA));
    }

    @Test
    void resetTokenWorksOnceAndBelongsToItsUser() {
        String token = service.issueResetToken(user);

        assertTrue(token.length() >= 43, "256 bits en base64 url-safe");
        assertEquals(Optional.of(user), service.consumeResetToken(token));
        assertTrue(service.consumeResetToken(token).isEmpty());
    }

    @Test
    void resetTokenExpiresAfter30Minutes() {
        String token = service.issueResetToken(user);

        clock.advance(Duration.ofMinutes(30));

        assertTrue(service.consumeResetToken(token).isEmpty());
    }

    @Test
    void garbageResetTokensAreRejected() {
        service.issueResetToken(user);

        assertTrue(service.consumeResetToken("no-es-un-token").isEmpty());
        assertTrue(service.consumeResetToken("  ").isEmpty());
        assertTrue(service.consumeResetToken(null).isEmpty());
    }
}
