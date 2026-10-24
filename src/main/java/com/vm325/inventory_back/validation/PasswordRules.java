package com.vm325.inventory_back.validation;

/**
 * Regla única de contraseña para altas, cambios y recuperación. El máximo de
 * 72 caracteres es el límite real de BCrypt (más allá se ignora).
 */
public final class PasswordRules {

    public static final String REGEX = "^(?=.*[A-Za-z])(?=.*\\d).{8,72}$";
    public static final String MESSAGE =
            "La contraseña debe tener entre 8 y 72 caracteres e incluir al menos una letra y un número";

    private PasswordRules() {
    }
}
