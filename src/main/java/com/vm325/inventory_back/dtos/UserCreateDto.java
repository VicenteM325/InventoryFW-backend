package com.vm325.inventory_back.dtos;

import com.vm325.inventory_back.validation.PasswordRules;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserCreateDto {
    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 100, message = "El nombre no puede superar 100 caracteres")
    private String name;

    @NotBlank(message = "El nombre de usuario es obligatorio")
    @Pattern(regexp = "^[A-Za-z0-9._-]{3,50}$",
            message = "El usuario debe tener entre 3 y 50 caracteres: letras, números, punto, guion o guion bajo")
    private String userName;

    @NotBlank(message = "La contraseña es obligatoria")
    @Pattern(regexp = PasswordRules.REGEX, message = PasswordRules.MESSAGE)
    private String password;

    /** Opcional. Si se envía, la cuenta nace con la verificación en dos pasos activa. */
    @Email(message = "El correo electrónico no es válido")
    @Size(max = 254, message = "El correo no puede superar 254 caracteres")
    private String email;

    @NotBlank(message = "El rol es obligatorio")
    private String roleName;
}
