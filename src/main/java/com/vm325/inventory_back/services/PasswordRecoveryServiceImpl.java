package com.vm325.inventory_back.services;

import com.vm325.inventory_back.entities.User;
import com.vm325.inventory_back.enums.VerificationType;
import com.vm325.inventory_back.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@RequiredArgsConstructor
public class PasswordRecoveryServiceImpl implements PasswordRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(PasswordRecoveryServiceImpl.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final VerificationCodeService verificationCodeService;
    private final MailService mailService;
    private final LoginAttemptService loginAttemptService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Override
    public void requestReset(String email) {
        User user = userRepository.findByEmailIgnoreCase(email.trim()).orElse(null);
        if (user == null || !user.isActive()) {
            return;
        }
        try {
            String token = verificationCodeService.issueResetToken(user);
            String link = UriComponentsBuilder.fromUriString(frontendUrl)
                    .path("/auth/reset-password")
                    .queryParam("token", token)
                    .build().toUriString();
            mailService.sendPasswordReset(user.getEmail(), user.getName(), link,
                    verificationCodeService.resetTtlMinutes());
        } catch (ResponseStatusException e) {
            // Cooldown activo o SMTP caído: se registra pero se responde igual
            // que si el correo no existiera, para no permitir enumerar cuentas.
            log.warn("Recuperación de contraseña no enviada a un usuario existente: {}", e.getReason());
        }
    }

    @Override
    @Transactional
    public void resetPassword(String token, String newPassword) {
        User user = verificationCodeService.consumeResetToken(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "El enlace es inválido o ya venció. Solicite uno nuevo"));
        if (!user.isActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El enlace es inválido o ya venció. Solicite uno nuevo");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        // Los códigos pendientes de antes del cambio ya no deben valer.
        verificationCodeService.invalidate(user, VerificationType.LOGIN_2FA);
        verificationCodeService.invalidate(user, VerificationType.PASSWORD_RESET);
        loginAttemptService.recordSuccess(user.getUserName());
    }
}
