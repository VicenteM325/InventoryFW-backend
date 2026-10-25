package com.vm325.inventory_back.reports;

import com.vm325.inventory_back.reports.dto.InventoryAlertRowDto;
import com.vm325.inventory_back.reports.dto.InventorySummaryDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

import static com.vm325.inventory_back.reports.ColumnType.*;

@Service
@RequiredArgsConstructor
public class InventoryReportServiceImpl implements InventoryReportService {

    private final InventoryAnalyticsService analytics;
    private final ReportWriterFactory reportWriterFactory;

    @Override
    public byte[] generate(ReportFormat format, String generatedBy) {
        return reportWriterFactory.get(format).write(buildDocument(analytics.summarize(), generatedBy));
    }

    ReportDocument buildDocument(InventorySummaryDto s, String generatedBy) {
        LocalDateTime now = LocalDateTime.now();

        List<ReportKpi> kpis = List.of(
                ReportKpi.of("Productos activos", ReportFormats.integer(s.totalProducts())),
                ReportKpi.of("Disponibles", ReportFormats.integer(s.available())),
                ReportKpi.of("Stock bajo", ReportFormats.integer(s.lowStock())),
                ReportKpi.of("Agotados", ReportFormats.integer(s.outOfStock())),
                ReportKpi.of("Alertas pendientes", ReportFormats.integer(s.pendingAlerts())));

        ReportSection products = new ReportSection("Estado del stock",
                List.of(ReportColumn.text("Producto", 4.5f), ReportColumn.text("Código", 2.6f),
                        ReportColumn.text("Categoría", 2),
                        ReportColumn.of("Stock", INTEGER, 1.3f), ReportColumn.of("Mínimo", INTEGER, 1.3f),
                        ReportColumn.of("Precio", MONEY, 1.8f), ReportColumn.of("Estado", STATUS, 2)),
                s.products().stream().map(p -> List.<Object>of(p.name(), p.barcode(), p.category(),
                        p.stock(), p.minStock(), p.price(), p.status())).toList(),
                List.of(), false);

        List<InventoryAlertRowDto> pending = s.alerts().stream()
                .filter(a -> "PENDIENTE".equals(String.valueOf(a.status()))).toList();
        ReportSection alerts = new ReportSection("Alertas de reposición pendientes",
                List.of(ReportColumn.text("Producto", 4), ReportColumn.of("Stock al generarse", INTEGER, 2),
                        ReportColumn.of("Generada", DATETIME, 2.6f)),
                pending.stream().map(a -> List.<Object>of(a.product(), a.stockAtAlert(), a.createdAt())).toList(),
                List.of(), false);

        return new ReportDocument("Reporte de Inventario",
                "Estado al " + now.format(ReportFormats.DATE_TIME) + " (solo productos activos)",
                generatedBy, now, kpis, List.of(products, alerts));
    }
}
