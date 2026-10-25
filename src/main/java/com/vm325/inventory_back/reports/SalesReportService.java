package com.vm325.inventory_back.reports;

import java.time.LocalDate;

public interface SalesReportService {

    /** Genera el reporte de ventas de [from, to] (ambos días incluidos) en el formato pedido. */
    byte[] generate(LocalDate from, LocalDate to, ReportFormat format, String generatedBy);
}
