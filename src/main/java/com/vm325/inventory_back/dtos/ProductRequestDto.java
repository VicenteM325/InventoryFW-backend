package com.vm325.inventory_back.dtos;

import com.vm325.inventory_back.enums.ProductCategory;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class ProductRequestDto {
    @NotBlank(message = "El nombre del producto es obligatorio")
    @Size(max = 100, message = "El nombre no puede superar 100 caracteres")
    private String name;

    @NotBlank(message = "El código de barras es obligatorio")
    @Size(max = 100, message = "El código de barras no puede superar 100 caracteres")
    private String barcode;

    @Size(max = 2000, message = "La descripción no puede superar 2000 caracteres")
    private String description;

    @NotNull(message = "El stock inicial es obligatorio")
    @Min(value = 0, message = "El stock no puede ser negativo")
    private Integer stock;

    @NotNull(message = "El precio es obligatorio")
    @DecimalMin(value = "0.0", message = "El precio no puede ser negativo")
    private BigDecimal price;

    @NotNull(message = "La categoría es obligatoria")
    private ProductCategory category;

    /** Opcional: si se omite, se usa el mínimo configurado por defecto. */
    @Min(value = 0, message = "El stock mínimo no puede ser negativo")
    private Integer minStock;

    /** Opcional: producto sin proveedor asignado. */
    private Long supplierId;
}
