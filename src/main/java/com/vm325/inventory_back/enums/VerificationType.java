package com.vm325.inventory_back.enums;

public enum VerificationType {
    /** Segundo paso del inicio de sesión. */
    LOGIN_2FA,
    /** Confirma la activación de 2FA (y que el correo pertenece al usuario). */
    ENABLE_2FA,
    /** Token de recuperación de contraseña enviado por enlace. */
    PASSWORD_RESET
}
