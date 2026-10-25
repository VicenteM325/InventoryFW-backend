package com.vm325.inventory_back.reports.dto;

import java.math.BigDecimal;

public record InventoryProductRowDto(Long productId, String name, String barcode, String category,
                                     String supplier, int stock, int minStock, BigDecimal price, String status) {
}
