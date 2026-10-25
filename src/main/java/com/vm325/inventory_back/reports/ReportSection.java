package com.vm325.inventory_back.reports;

import java.util.List;

/**
 * Bloque de un reporte: una tabla con su título y líneas de resumen al pie. Las celdas son objetos tipados
 * (String, Integer, BigDecimal, LocalDate, LocalDateTime o null) para que cada
 * formato las presente a su manera: texto formateado en PDF, valores nativos en Excel.
 *
 * @param totalsRow si es true se agrega una fila "Total" que suma las columnas numéricas
 */
public record ReportSection(
        String title,
        List<ReportColumn> columns,
        List<List<Object>> rows,
        List<String> footerLines,
        boolean totalsRow
) {
}
