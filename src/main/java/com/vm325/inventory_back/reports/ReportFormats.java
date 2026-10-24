package com.vm325.inventory_back.reports;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Formato de valores de celda como texto (PDF) con las mismas reglas en todo el sistema. */
public final class ReportFormats {

    public static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    public static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private ReportFormats() {
    }

    public static String money(BigDecimal value) {
        return "Q " + String.format(Locale.US, "%,.2f", value);
    }

    public static String integer(Number value) {
        return String.format(Locale.US, "%,d", value.longValue());
    }

    /** Texto de una celda según el tipo de su columna; null se muestra como "-". */
    public static String cell(Object value, ColumnType type) {
        if (value == null) {
            return "-";
        }
        return switch (type) {
            case MONEY -> money(value instanceof BigDecimal b ? b : new BigDecimal(value.toString()));
            case INTEGER -> integer((Number) value);
            case DATE -> ((LocalDate) value).format(DATE);
            case DATETIME -> ((LocalDateTime) value).format(DATE_TIME);
            case STATUS -> status(value.toString());
            default -> value.toString();
        };
    }

    /** Etiqueta legible de un estado interno (STOCK_BAJO -> Stock bajo). */
    public static String status(String raw) {
        if (raw == null || raw.isBlank()) {
            return "-";
        }
        String text = raw.replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
