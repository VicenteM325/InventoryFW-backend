package com.vm325.inventory_back.reports;

/** Indicador del resumen de un reporte: concepto y valor ya formateado. */
public record ReportKpi(String label, String value) {

    public static ReportKpi of(String label, String value) {
        return new ReportKpi(label, value);
    }
}
