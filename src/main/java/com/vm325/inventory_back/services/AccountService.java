package com.vm325.inventory_back.services;

import com.vm325.inventory_back.dtos.UserResponseDto;

/** Operaciones de un usuario autenticado sobre su propia cuenta. */
public interface AccountService {

    UserResponseDto updateEmail(String email, String currentPassword);

    /** Envía al correo de la cuenta el código que confirma la activación de 2FA. */
    void requestEnableTwoFactor();

    UserResponseDto confirmEnableTwoFactor(String code);

    UserResponseDto disableTwoFactor(String currentPassword);

    void changePassword(String currentPassword, String newPassword);
}
