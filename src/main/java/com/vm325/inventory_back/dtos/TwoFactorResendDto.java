package com.vm325.inventory_back.dtos;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TwoFactorResendDto {
    @NotBlank(message = "La sesión de verificación es obligatoria")
    private String challengeToken;
}
