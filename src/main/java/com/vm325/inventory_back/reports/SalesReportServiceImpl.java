package com.vm325.inventory_back.reports;

import com.vm325.inventory_back.reports.dto.SalesSummaryDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static com.vm325.inventory_back.reports.ColumnType.*;

@Service
@RequiredArgsConstructor
public class SalesReportServiceImpl implements SalesReportService {

    private final SalesAnalyticsService analytics;
    private final ReportWriterFactory reportWriterFactory;

    @Override
    public byte[] generate(LocalDate from, LocalDate to, ReportFormat format, String generatedBy) {
        SalesSummaryDto s = analytics.summarize(from, to);
        return reportWriterFactory.get(format).write(buildDocument(s, generatedBy));
    }

    ReportDocument buildDocument(SalesSummaryDto s, String generatedBy) {
        String subtitle = "Periodo: " + s.from().format(ReportFormats.DATE) + " al " + s.to().format(ReportFormats.DATE);

        List<ReportSection> sections = new ArrayList<>();

        sections.add(new ReportSection("Ventas por día",
                List.of(ReportColumn.of("Fecha", DATE, 3), ReportColumn.of("Transacciones", INTEGER, 2),
                        ReportColumn.of("Total", MONEY, 2)),
                s.byDay().stream().map(d -> List.<Object>of(d.date(), d.transactions(), d.total())).toList(),
                List.of(), true));

        sections.add(new ReportSection("Productos más vendidos",
                List.of(ReportColumn.text("Producto", 6), ReportColumn.of("Unidades", INTEGER, 2),
                        ReportColumn.of("Ingresos", MONEY, 2)),
                s.topProducts().stream().map(p -> List.<Object>of(p.name(), p.units(), p.revenue())).toList(),
                List.of(), false));

        sections.add(new ReportSection("Detalle de ventas",
                List.of(ReportColumn.of("Fecha", DATETIME, 2.4f), ReportColumn.text("Empleado", 2),
                        ReportColumn.text("Cliente", 2.6f), ReportColumn.text("Productos", 6),
                        ReportColumn.of("Unidades", INTEGER, 1.4f), ReportColumn.of("Total", MONEY, 2)),
                s.sales().stream().map(r -> List.<Object>of(r.date(), r.employee(), r.customer(), r.products(),
                        r.units(), r.total())).toList(),
                List.of(), true));

        return new ReportDocument("Reporte de Ventas", subtitle, generatedBy, LocalDateTime.now(), List.of(), sections);
    }
}
