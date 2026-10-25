package com.vm325.inventory_back.reports.dto;

import java.math.BigDecimal;

public record ProductSalesDto(Long productId, String name, int units, BigDecimal revenue) {
}
