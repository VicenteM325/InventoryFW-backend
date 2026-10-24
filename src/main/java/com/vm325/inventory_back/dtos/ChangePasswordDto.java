package com.vm325.inventory_back.dtos;

import com.vm325.inventory_back.validation.PasswordRules;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChangePasswordDto {
    @NotBlank(message = "La contraseña actual es obligatoria")
    private String currentPassword;

    @NotBlank(message = "La nueva contraseña es obligatoria")
    @Pattern(regexp = PasswordRules.REGEX, message = PasswordRules.MESSAGE)
    private String newPassword;
}
