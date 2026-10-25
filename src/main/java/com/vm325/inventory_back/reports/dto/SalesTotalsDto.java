package com.vm325.inventory_back.reports.dto;

import java.math.BigDecimal;

/** Totales de un periodo de ventas. */
public record SalesTotalsDto(BigDecimal total, int transactions, BigDecimal averageTicket, int units) {
}
