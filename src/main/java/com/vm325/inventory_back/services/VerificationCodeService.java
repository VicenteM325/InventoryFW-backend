package com.vm325.inventory_back.services;

import com.vm325.inventory_back.entities.User;
import com.vm325.inventory_back.entities.VerificationCode;
import com.vm325.inventory_back.enums.VerificationType;
import com.vm325.inventory_back.repositories.VerificationCodeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Emite y verifica códigos de un solo uso (OTP de 6 dígitos para 2FA) y
 * tokens de recuperación de contraseña. Solo se persiste un HMAC-SHA256 del
 * valor (con el secreto del servidor como clave), de modo que una fuga de la
 * tabla no permite adivinar los códigos de 6 dígitos por fuerza bruta.
 *
 * verifyOtp no lanza excepciones a propósito: el contador de intentos debe
 * quedar guardado aunque el código sea incorrecto, y una excepción de
 * runtime revertiría la transacción. El llamador traduce el resultado.
 */
@Service
public class VerificationCodeService {

    public enum Result { VALID, INVALID, LOCKED }

    private final VerificationCodeRepository repository;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    @Value("${jwt.secret}")
    private String hmacSecret;

    @Value("${security.otp.ttl-minutes}")
    private int otpTtlMinutes;

    @Value("${security.otp.max-attempts}")
    private int maxAttempts;

    @Value("${security.otp.resend-cooldown-seconds}")
    private int cooldownSeconds;

    @Value("${security.reset.ttl-minutes}")
    private int resetTtlMinutes;

    @Autowired
    public VerificationCodeService(VerificationCodeRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public int otpTtlMinutes() {
        return otpTtlMinutes;
    }

    public int resetTtlMinutes() {
        return resetTtlMinutes;
    }

    /** Genera un código numérico de 6 dígitos, invalidando los anteriores del mismo tipo. */
    @Transactional
    public String issueOtp(User user, VerificationType type) {
        String code = String.format("%06d", random.nextInt(1_000_000));
        persist(user, type, hash(type, user, code), Duration.ofMinutes(otpTtlMinutes));
        return code;
    }

    /** Genera un token aleatorio de 256 bits (URL-safe) para el enlace de recuperación. */
    @Transactional
    public String issueResetToken(User user) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        persist(user, VerificationType.PASSWORD_RESET, hash(VerificationType.PASSWORD_RESET, null, token),
                Duration.ofMinutes(resetTtlMinutes));
        return token;
    }

    @Transactional
    public Result verifyOtp(User user, VerificationType type, String code) {
        Optional<VerificationCode> active = repository
                .findFirstByUserAndTypeAndConsumedFalseOrderByCreatedAtDesc(user, type);
        if (active.isEmpty() || !clock.instant().isBefore(active.get().getExpiresAt())) {
            return Result.INVALID;
        }
        VerificationCode entry = active.get();

        if (code != null && matches(entry.getCodeHash(), hash(type, user, code.trim()))) {
            entry.setConsumed(true);
            repository.save(entry);
            return Result.VALID;
        }

        entry.setAttempts(entry.getAttempts() + 1);
        if (entry.getAttempts() >= maxAttempts) {
            // Se agotaron los intentos: el código muere y hay que pedir otro.
            entry.setConsumed(true);
            repository.save(entry);
            return Result.LOCKED;
        }
        repository.save(entry);
        return Result.INVALID;
    }

    /** Valida y consume un token de recuperación; devuelve el usuario dueño si era válido. */
    @Transactional
    public Optional<User> consumeResetToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        Optional<VerificationCode> found = repository.findFirstByCodeHashAndTypeAndConsumedFalse(
                hash(VerificationType.PASSWORD_RESET, null, token.trim()), VerificationType.PASSWORD_RESET);
        if (found.isEmpty() || !clock.instant().isBefore(found.get().getExpiresAt())) {
            return Optional.empty();
        }
        VerificationCode entry = found.get();
        entry.setConsumed(true);
        repository.save(entry);
        return Optional.of(entry.getUser());
    }

    /** Invalida cualquier código vigente de ese tipo (p.ej. tras cambiar la contraseña). */
    @Transactional
    public void invalidate(User user, VerificationType type) {
        repository.consumeActive(user, type);
    }

    private void persist(User user, VerificationType type, String codeHash, Duration ttl) {
        Instant now = clock.instant();
        repository.findFirstByUserAndTypeOrderByCreatedAtDesc(user, type).ifPresent(last -> {
            // El cooldown frena el reenvío masivo mientras haya un código pendiente;
            // uno ya usado no debe impedir un nuevo inicio de sesión inmediato.
            long elapsed = Duration.between(last.getCreatedAt(), now).getSeconds();
            if (!last.isConsumed() && elapsed < cooldownSeconds) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                        "Espere " + (cooldownSeconds - elapsed) + " segundos antes de solicitar otro código");
            }
        });
        repository.consumeActive(user, type);
        repository.deleteExpiredBefore(now.minus(Duration.ofDays(1)));
        repository.save(VerificationCode.builder()
                .user(user)
                .type(type)
                .codeHash(codeHash)
                .createdAt(now)
                .expiresAt(now.plus(ttl))
                .attempts(0)
                .consumed(false)
                .build());
    }

    private boolean matches(String storedHash, String candidateHash) {
        return MessageDigest.isEqual(
                storedHash.getBytes(StandardCharsets.UTF_8), candidateHash.getBytes(StandardCharsets.UTF_8));
    }

    private String hash(VerificationType type, User user, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String scope = user == null ? type.name() : type.name() + ":" + user.getUserId();
            return HexFormat.of().formatHex(mac.doFinal((scope + ":" + value).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 no disponible", e);
        }
    }
}
