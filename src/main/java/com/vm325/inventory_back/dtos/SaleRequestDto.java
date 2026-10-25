package com.vm325.inventory_back.dtos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class SaleRequestDto {
    private String customerName;

    /** Opcional: si viene, se envía el recibo de la venta a ese correo. No se guarda. */
    @Email(message = "El correo electrónico del cliente no es válido")
    @Size(max = 254, message = "El correo del cliente no puede superar 254 caracteres")
    private String customerEmail;

    @NotEmpty(message = "La venta debe incluir al menos un producto")
    @Valid
    private List<SaleItemRequestDto> items;
}
