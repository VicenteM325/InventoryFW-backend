package com.vm325.inventory_back.reports.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Resumen analítico de ventas de un periodo y su comparación con el periodo
 * anterior de igual duración. Los *ChangePct son porcentajes (12.5 = +12.5%)
 * y son null cuando el periodo anterior no tuvo ventas (no hay base para comparar).
 */
public record SalesSummaryDto(
        LocalDate from,
        LocalDate to,
        LocalDate previousFrom,
        LocalDate previousTo,
        SalesTotalsDto current,
        SalesTotalsDto previous,
        BigDecimal totalChangePct,
        BigDecimal transactionsChangePct,
        BigDecimal averageTicketChangePct,
        BigDecimal unitsChangePct,
        List<DailySalesDto> byDay,
        List<EmployeeSalesDto> byEmployee,
        List<ProductSalesDto> topProducts,
        List<SaleRowDto> sales
) {
}
