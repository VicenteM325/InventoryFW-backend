package com.vm325.inventory_back.reports;

import com.vm325.inventory_back.reports.dto.SalesSummaryDto;

import java.time.LocalDate;

public interface SalesAnalyticsService {

    /**
     * Resume las ventas de [from, to] (ambos días incluidos) y las compara con el
     * periodo inmediatamente anterior de la misma duración.
     */
    SalesSummaryDto summarize(LocalDate from, LocalDate to);
}
