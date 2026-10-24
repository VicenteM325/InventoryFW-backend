package com.vm325.inventory_back.reports;

import com.vm325.inventory_back.reports.dto.InventorySummaryDto;

public interface InventoryAnalyticsService {

    /** Estado actual del inventario de productos activos y de sus alertas de reposición. */
    InventorySummaryDto summarize();
}
