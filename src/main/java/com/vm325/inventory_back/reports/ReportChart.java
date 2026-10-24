package com.vm325.inventory_back.reports;

import java.math.BigDecimal;
import java.util.List;

/** Gráfico de barras sencillo: una serie con una etiqueta por barra. */
public record ReportChart(String title, List<String> labels, List<BigDecimal> values, String seriesName, boolean money) {
}
