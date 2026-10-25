package com.vm325.inventory_back.dtos;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SupplierRequestDto {
    @NotBlank(message = "El nombre del proveedor es obligatorio")
    private String name;

    /** Persona de contacto (opcional). */
    private String contactName;

    @NotBlank(message = "El correo del proveedor es obligatorio")
    @Email(message = "El correo del proveedor no es válido")
    @Size(max = 254, message = "El correo no puede superar 254 caracteres")
    private String email;

    @NotBlank(message = "El teléfono del proveedor es obligatorio")
    @Pattern(regexp = "^[0-9+()\\- ]{8,20}$",
            message = "El teléfono debe tener entre 8 y 20 caracteres: números, espacios, +, - o paréntesis")
    private String phone;
}
