package com.vm325.inventory_back.services;

/** Recuperación de contraseña olvidada mediante un enlace enviado por correo. */
public interface PasswordRecoveryService {

    /**
     * Si existe una cuenta activa con ese correo, le envía el enlace. Nunca
     * revela si el correo existe: el resultado es el mismo en todos los casos.
     */
    void requestReset(String email);

    /** Cambia la contraseña con un token válido (un solo uso). */
    void resetPassword(String token, String newPassword);
}
