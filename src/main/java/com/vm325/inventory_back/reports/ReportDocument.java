package com.vm325.inventory_back.reports;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Reporte completo, agnóstico de formato: encabezado, resumen y secciones.
 * PdfReportWriter y XlsxReportWriter consumen exactamente esta estructura, así que
 * agregar un reporte nuevo no requiere tocar ninguno de los dos.
 */
public record ReportDocument(
        String title,
        String subtitle,
        String generatedBy,
        LocalDateTime generatedAt,
        List<ReportKpi> kpis,
        List<ReportSection> sections
) {
}
