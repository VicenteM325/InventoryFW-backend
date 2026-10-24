package com.vm325.inventory_back.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TwoFactorVerifyDto {
    @NotBlank(message = "La sesión de verificación es obligatoria")
    private String challengeToken;

    @NotBlank(message = "El código es obligatorio")
    @Pattern(regexp = "^\\d{6}$", message = "El código debe tener 6 dígitos")
    private String code;
}
