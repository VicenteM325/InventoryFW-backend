package com.vm325.inventory_back.reports.dto;

import java.time.LocalDateTime;

public record InventoryAlertRowDto(Long id, String product, int stockAtAlert, String status,
                                   LocalDateTime createdAt, LocalDateTime attendedAt) {
}
