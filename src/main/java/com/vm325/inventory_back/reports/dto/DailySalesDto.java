package com.vm325.inventory_back.reports.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Ventas de un día; los días sin ventas del periodo se incluyen en cero. */
public record DailySalesDto(LocalDate date, BigDecimal total, int transactions) {
}
