package com.vm325.inventory_back.reports;

/** Tipo de dato de una columna: define cómo se alinea, se formatea y (en Excel) se almacena. */
public enum ColumnType {
    TEXT,
    INTEGER,
    /** Quetzales: BigDecimal formateado como "Q 1,234.50" (número con formato de moneda en Excel). */
    MONEY,
    DATE,
    DATETIME,
    /** Estado con color: DISPONIBLE/ATENDIDA en verde, STOCK_BAJO/PENDIENTE en ámbar, AGOTADO en rojo. */
    STATUS;

    public boolean isNumeric() {
        return this == INTEGER || this == MONEY;
    }
}
