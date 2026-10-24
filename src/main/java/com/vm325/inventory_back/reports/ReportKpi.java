package com.vm325.inventory_back.reports;

/** Indicador destacado (tarjeta) con una nota opcional, p. ej. la variación contra el periodo anterior. */
public record ReportKpi(String label, String value, String note, Trend trend) {

    public enum Trend { UP, DOWN, NEUTRAL }

    public static ReportKpi of(String label, String value) {
        return new ReportKpi(label, value, null, Trend.NEUTRAL);
    }
}
