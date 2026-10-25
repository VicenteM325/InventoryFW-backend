package com.vm325.inventory_back.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Respuesta del login. Sin 2FA: message es el rol y la sesión queda en la
 * cookie. Con 2FA: twoFactorRequired=true, aún no hay sesión y el cliente
 * debe completar /auth/2fa/verify con challengeToken y el código del correo.
 */
@Getter
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LoginResponseDto {
    private String message;
    private boolean twoFactorRequired;
    private String challengeToken;
    private String emailHint;

    public static LoginResponseDto authenticated(String roleName) {
        return new LoginResponseDto(roleName, false, null, null);
    }

    public static LoginResponseDto challenge(String challengeToken, String emailHint) {
        return new LoginResponseDto("Se envió un código de verificación a su correo", true, challengeToken, emailHint);
    }
}
