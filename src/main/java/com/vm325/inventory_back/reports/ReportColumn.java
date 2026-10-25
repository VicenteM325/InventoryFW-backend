package com.vm325.inventory_back.reports;

/**
 * Columna de una tabla de reporte. weight reparte el ancho disponible entre las
 * columnas de forma proporcional (una columna de peso 4 es el doble de ancha que una de 2).
 */
public record ReportColumn(String header, ColumnType type, float weight) {

    public static ReportColumn text(String header, float weight) {
        return new ReportColumn(header, ColumnType.TEXT, weight);
    }

    public static ReportColumn of(String header, ColumnType type, float weight) {
        return new ReportColumn(header, type, weight);
    }
}
