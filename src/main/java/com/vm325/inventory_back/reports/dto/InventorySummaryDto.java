package com.vm325.inventory_back.reports.dto;

import java.util.List;
import java.util.Map;

/** Estado del inventario activo: conteos por estado y categoría, productos y alertas. */
public record InventorySummaryDto(
        int totalProducts,
        int available,
        int lowStock,
        int outOfStock,
        int pendingAlerts,
        Map<String, Integer> byCategory,
        List<InventoryProductRowDto> products,
        List<InventoryAlertRowDto> alerts
) {
}
