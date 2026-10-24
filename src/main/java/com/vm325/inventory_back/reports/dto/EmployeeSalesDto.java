package com.vm325.inventory_back.reports.dto;

import java.math.BigDecimal;

public record EmployeeSalesDto(String name, String userName, BigDecimal total, int transactions) {
}
