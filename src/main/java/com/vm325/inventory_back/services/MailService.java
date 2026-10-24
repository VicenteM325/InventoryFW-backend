package com.vm325.inventory_back.services;

public interface MailService {

    /** Código de verificación del segundo paso del login. */
    void sendLoginCode(String to, String name, String code, int validMinutes);

    /** Código para confirmar la activación de 2FA (verifica además que el correo es del usuario). */
    void sendEnableTwoFactorCode(String to, String name, String code, int validMinutes);

    /** Enlace de recuperación de contraseña. */
    void sendPasswordReset(String to, String name, String link, int validMinutes);
}
