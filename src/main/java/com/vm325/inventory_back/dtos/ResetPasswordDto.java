package com.vm325.inventory_back.dtos;

import com.vm325.inventory_back.validation.PasswordRules;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResetPasswordDto {
    @NotBlank(message = "El token es obligatorio")
    @Size(max = 200, message = "El token no es válido")
    private String token;

    @NotBlank(message = "La contraseña es obligatoria")
    @Pattern(regexp = PasswordRules.REGEX, message = PasswordRules.MESSAGE)
    private String newPassword;
}
