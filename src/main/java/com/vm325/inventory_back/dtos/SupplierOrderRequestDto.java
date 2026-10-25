package com.vm325.inventory_back.dtos;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SupplierOrderRequestDto {

    @NotNull(message = "La cantidad a pedir es obligatoria")
    @Min(value = 1, message = "La cantidad a pedir debe ser al menos 1")
    @Max(value = 100000, message = "La cantidad a pedir no puede superar 100000")
    private Integer quantity;
}
