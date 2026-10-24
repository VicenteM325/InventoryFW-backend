package com.vm325.inventory_back.services;

import com.vm325.inventory_back.dtos.UserResponseDto;
import com.vm325.inventory_back.entities.User;
import com.vm325.inventory_back.enums.VerificationType;
import com.vm325.inventory_back.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AccountServiceImpl implements AccountService {

    private final UserService userService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final VerificationCodeService verificationCodeService;
    private final MailService mailService;

    @Override
    public UserResponseDto updateEmail(String email, String currentPassword) {
        User user = userService.getUserDetails();
        requirePassword(user, currentPassword);

        String normalized = email.trim().toLowerCase();
        userRepository.findByEmailIgnoreCase(normalized).ifPresent(other -> {
            if (!other.getUserId().equals(user.getUserId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Ese correo ya está en uso");
            }
        });

        if (!normalized.equals(user.getEmail())) {
            user.setEmail(normalized);
            // El correo nuevo no está verificado: 2FA se vuelve a confirmar con él.
            user.setTwoFactorEnabled(false);
            verificationCodeService.invalidate(user, VerificationType.LOGIN_2FA);
            verificationCodeService.invalidate(user, VerificationType.ENABLE_2FA);
            userRepository.save(user);
        }
        return UserResponseDto.from(user);
    }

    @Override
    public void requestEnableTwoFactor() {
        User user = userService.getUserDetails();
        if (user.getEmail() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Registre un correo electrónico antes de activar la verificación en dos pasos");
        }
        if (user.isTwoFactorEnabled()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La verificación en dos pasos ya está activa");
        }
        String code = verificationCodeService.issueOtp(user, VerificationType.ENABLE_2FA);
        mailService.sendEnableTwoFactorCode(user.getEmail(), user.getName(), code,
                verificationCodeService.otpTtlMinutes());
    }

    @Override
    public UserResponseDto confirmEnableTwoFactor(String code) {
        User user = userService.getUserDetails();
        switch (verificationCodeService.verifyOtp(user, VerificationType.ENABLE_2FA, code)) {
            case VALID:
                user.setTwoFactorEnabled(true);
                userRepository.save(user);
                return UserResponseDto.from(user);
            case LOCKED:
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                        "Demasiados intentos fallidos. Solicite un nuevo código");
            default:
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Código incorrecto o vencido");
        }
    }

    @Override
    public UserResponseDto disableTwoFactor(String currentPassword) {
        User user = userService.getUserDetails();
        requirePassword(user, currentPassword);
        user.setTwoFactorEnabled(false);
        userRepository.save(user);
        verificationCodeService.invalidate(user, VerificationType.LOGIN_2FA);
        return UserResponseDto.from(user);
    }

    private void requirePassword(User user, String currentPassword) {
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La contraseña actual es incorrecta");
        }
    }
}
