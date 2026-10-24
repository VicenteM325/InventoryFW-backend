package com.vm325.inventory_back.reports;

import java.awt.Color;

/** Paleta y nombre de marca compartidos por los exportadores (PDF y Excel) para que se vean iguales. */
public final class ReportTheme {

    public static final String BRAND = "Multiservicios Wilson";

    public static final Color INK = new Color(0x1F, 0x29, 0x37);
    public static final Color ACCENT = new Color(0x25, 0x63, 0xEB);
    public static final Color GREEN = new Color(0x15, 0x80, 0x3D);
    public static final Color AMBER = new Color(0xB4, 0x53, 0x09);
    public static final Color RED = new Color(0xB9, 0x1C, 0x1C);
    public static final Color MUTED = new Color(0x6B, 0x72, 0x80);
    public static final Color ZEBRA = new Color(0xF3, 0xF4, 0xF6);
    public static final Color BORDER = new Color(0xE5, 0xE7, 0xEB);
    public static final Color CARD = new Color(0xF9, 0xFA, 0xFB);
    public static final Color WHITE = Color.WHITE;

    private ReportTheme() {
    }

    /** Color de un estado: lo bueno en verde, lo que requiere atención en ámbar y lo crítico en rojo. */
    public static Color statusColor(String status) {
        if (status == null) {
            return INK;
        }
        return switch (status) {
            case "DISPONIBLE", "ATENDIDA" -> GREEN;
            case "STOCK_BAJO", "PENDIENTE" -> AMBER;
            case "AGOTADO" -> RED;
            default -> INK;
        };
    }

    public static Color trendColor(ReportKpi.Trend trend) {
        return switch (trend) {
            case UP -> GREEN;
            case DOWN -> RED;
            default -> MUTED;
        };
    }
}
