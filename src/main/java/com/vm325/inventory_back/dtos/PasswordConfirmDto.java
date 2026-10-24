package com.vm325.inventory_back.dtos;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PasswordConfirmDto {
    @NotBlank(message = "La contraseña actual es obligatoria")
    private String currentPassword;
}
