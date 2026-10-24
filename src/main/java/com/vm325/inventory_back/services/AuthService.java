package com.vm325.inventory_back.services;

import com.vm325.inventory_back.dtos.LoginResponseDto;
import com.vm325.inventory_back.entities.User;
import com.vm325.inventory_back.enums.VerificationType;
import com.vm325.inventory_back.jwt.JwtUtil;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final AuthenticationManagerBuilder authenticationManagerBuilder;
    private final CookieService cookieService;
    private final VerificationCodeService verificationCodeService;
    private final MailService mailService;

    @Autowired
    public AuthService(UserService userService, JwtUtil jwtUtil,
                       AuthenticationManagerBuilder authenticationManagerBuilder, CookieService cookieService,
                       VerificationCodeService verificationCodeService, MailService mailService) {
        this.userService = userService;
        this.jwtUtil = jwtUtil;
        this.authenticationManagerBuilder = authenticationManagerBuilder;
        this.cookieService = cookieService;
        this.verificationCodeService = verificationCodeService;
        this.mailService = mailService;
    }

    /**
     * Primer paso del login. Valida usuario y contraseña (lanza
     * AuthenticationException si no coinciden). Si la cuenta tiene 2FA, envía
     * el código por correo y devuelve un challenge sin abrir sesión; si no,
     * abre la sesión de inmediato.
     */
    public LoginResponseDto login(String username, String password, HttpServletResponse response) {
        authenticationManagerBuilder.getObject()
                .authenticate(new UsernamePasswordAuthenticationToken(username, password));

        User user = userService.findByUserName(username);
        if (user.isTwoFactorEnabled()) {
            if (user.getEmail() == null) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "La verificación en dos pasos no está disponible para esta cuenta. Contacte al administrador");
            }
            sendLoginCode(user);
            return LoginResponseDto.challenge(jwtUtil.generateChallengeToken(user.getUserName()),
                    maskEmail(user.getEmail()));
        }
        return openSession(user, response);
    }

    /** Segundo paso: valida el código y recién entonces abre la sesión. */
    public LoginResponseDto verifyTwoFactor(String challengeToken, String code, HttpServletResponse response) {
        User user = resolveChallengeUser(challengeToken);
        switch (verificationCodeService.verifyOtp(user, VerificationType.LOGIN_2FA, code)) {
            case VALID:
                return openSession(user, response);
            case LOCKED:
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                        "Demasiados intentos fallidos. Inicie sesión de nuevo para recibir otro código");
            default:
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Código incorrecto o vencido");
        }
    }

    public void resendTwoFactor(String challengeToken) {
        sendLoginCode(resolveChallengeUser(challengeToken));
    }

    public void logout(HttpServletResponse response) {
        cookieService.deleteCookie("jwt", response);
        SecurityContextHolder.clearContext();
    }

    private void sendLoginCode(User user) {
        String code = verificationCodeService.issueOtp(user, VerificationType.LOGIN_2FA);
        mailService.sendLoginCode(user.getEmail(), user.getName(), code, verificationCodeService.otpTtlMinutes());
    }

    private User resolveChallengeUser(String challengeToken) {
        String username;
        try {
            username = jwtUtil.extractChallengeUser(challengeToken);
        } catch (JwtException | IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "La verificación expiró. Inicie sesión de nuevo");
        }
        User user = userService.findByUserName(username);
        if (!user.isActive() || !user.isTwoFactorEnabled()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "La verificación expiró. Inicie sesión de nuevo");
        }
        return user;
    }

    private LoginResponseDto openSession(User user, HttpServletResponse response) {
        cookieService.addHttpOnlyCookie("jwt", jwtUtil.generateToken(user.getUserName()),
                jwtUtil.getExpirationSeconds(), response);
        return LoginResponseDto.authenticated(user.getRole().getName().toString());
    }

    /** ana.perez@gmail.com -> a***@gmail.com */
    static String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***" + email.substring(Math.max(at, 0));
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
