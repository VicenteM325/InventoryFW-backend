package com.vm325.inventory_back.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class SaleResponseDto {private Long salesId;
    private String employeeUsername;
    private String customerName;
    private LocalDateTime date;
    private BigDecimal total;
    private List<SaleItemResponseDto> items;
    private List<Long> stockAlertsGeneratedFor; // productId de los productos que quedaron en alerta

    /** ENVIADO, NO_ENVIADO u OMITIDO (no se pidió recibo). Lo fija el controlador tras confirmar la venta. */
    @Setter
    private String receiptStatus;
}
