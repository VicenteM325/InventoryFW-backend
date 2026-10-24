package com.vm325.inventory_back.reports.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Una venta del periodo, lista para mostrarse en una tabla. */
public record SaleRowDto(Long id, LocalDateTime date, String employee, String customer, String products,
                         int units, BigDecimal total) {
}
