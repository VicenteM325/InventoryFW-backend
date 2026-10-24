package com.vm325.inventory_back.reports;

import com.vm325.inventory_back.reports.ReportKpi.Trend;
import com.vm325.inventory_back.reports.dto.InventorySummaryDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
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
                new ReportKpi("Disponibles", ReportFormats.integer(s.available()), share(s.available(), s.totalProducts()), Trend.NEUTRAL),
                new ReportKpi("Stock bajo", ReportFormats.integer(s.lowStock()), share(s.lowStock(), s.totalProducts()),
                        s.lowStock() > 0 ? Trend.DOWN : Trend.NEUTRAL),
                new ReportKpi("Agotados", ReportFormats.integer(s.outOfStock()), share(s.outOfStock(), s.totalProducts()),
                        s.outOfStock() > 0 ? Trend.DOWN : Trend.NEUTRAL),
                new ReportKpi("Alertas pendientes", ReportFormats.integer(s.pendingAlerts()),
                        s.pendingAlerts() > 0 ? "Requieren reposición" : "Todo en orden",
                        s.pendingAlerts() > 0 ? Trend.DOWN : Trend.UP));

        ReportChart chart = new ReportChart("Productos por estado de stock",
                List.of("Disponibles", "Stock bajo", "Agotados"),
                List.of(BigDecimal.valueOf(s.available()), BigDecimal.valueOf(s.lowStock()), BigDecimal.valueOf(s.outOfStock())),
                "Productos", false);

        ReportSection products = new ReportSection("Estado del stock",
                List.of(ReportColumn.text("Producto", 4), ReportColumn.text("Código de barras", 2.6f),
                        ReportColumn.text("Categoría", 2), ReportColumn.text("Proveedor", 3),
                        ReportColumn.of("Stock", INTEGER, 1.3f), ReportColumn.of("Mínimo", INTEGER, 1.3f),
                        ReportColumn.of("Precio", MONEY, 1.8f), ReportColumn.of("Estado", STATUS, 2)),
                s.products().stream().map(p -> List.<Object>of(p.name(), p.barcode(), p.category(), p.supplier(),
                        p.stock(), p.minStock(), p.price(), p.status())).toList(),
                List.of(), chart, false);

        ReportSection alerts = new ReportSection("Alertas de reposición",
                List.of(ReportColumn.text("Producto", 4), ReportColumn.of("Stock al generarse", INTEGER, 2),
                        ReportColumn.of("Estado", STATUS, 2), ReportColumn.of("Generada", DATETIME, 2.6f),
                        ReportColumn.of("Atendida", DATETIME, 2.6f)),
                // Arrays.asList admite null: una alerta sin atender no tiene fecha de atención.
                s.alerts().stream().map(a -> Arrays.<Object>asList(a.product(), a.stockAtAlert(), a.status(),
                        a.createdAt(), a.attendedAt())).toList(),
                List.of("Pendientes: " + s.pendingAlerts() + "   |   Atendidas: " + (s.alerts().size() - s.pendingAlerts())),
                null, false);

        return new ReportDocument("Reporte de Inventario",
                "Estado al " + now.format(ReportFormats.DATE_TIME) + "   |   Solo productos activos",
                generatedBy, now, kpis, List.of(products, alerts));
    }

    private String share(int part, int total) {
        if (total == 0) {
            return "0% del inventario";
        }
        return Math.round(part * 100.0 / total) + "% del inventario";
    }
}
