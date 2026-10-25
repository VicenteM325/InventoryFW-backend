package com.vm325.inventory_back.dtos;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserUpdateDto {
    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 100, message = "El nombre no puede superar 100 caracteres")
    private String name;

    /** Vacío o nulo quita el correo (y con él la verificación en dos pasos). */
    @Email(message = "El correo electrónico no es válido")
    @Size(max = 254, message = "El correo no puede superar 254 caracteres")
    private String email;

    @NotBlank(message = "El rol es obligatorio")
    private String roleName;
}
