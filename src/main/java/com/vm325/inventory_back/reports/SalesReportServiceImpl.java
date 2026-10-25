package com.vm325.inventory_back.reports;

import com.vm325.inventory_back.reports.ReportKpi.Trend;
import com.vm325.inventory_back.reports.dto.SalesSummaryDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static com.vm325.inventory_back.reports.ColumnType.*;

@Service
@RequiredArgsConstructor
public class SalesReportServiceImpl implements SalesReportService {

    private static final DateTimeFormatter SHORT_DAY = DateTimeFormatter.ofPattern("dd/MM");

    private final SalesAnalyticsService analytics;
    private final ReportWriterFactory reportWriterFactory;

    @Override
    public byte[] generate(LocalDate from, LocalDate to, ReportFormat format, String generatedBy) {
        SalesSummaryDto s = analytics.summarize(from, to);
        return reportWriterFactory.get(format).write(buildDocument(s, generatedBy));
    }

    ReportDocument buildDocument(SalesSummaryDto s, String generatedBy) {
        String subtitle = "Periodo: " + s.from().format(ReportFormats.DATE) + " al " + s.to().format(ReportFormats.DATE)
                + "   |   Comparado con: " + s.previousFrom().format(ReportFormats.DATE)
                + " al " + s.previousTo().format(ReportFormats.DATE);

        List<ReportKpi> kpis = List.of(
                kpi("Total vendido", ReportFormats.money(s.current().total()), s.totalChangePct()),
                kpi("Transacciones", ReportFormats.integer(s.current().transactions()), s.transactionsChangePct()),
                kpi("Ticket promedio", ReportFormats.money(s.current().averageTicket()), s.averageTicketChangePct()),
                kpi("Unidades vendidas", ReportFormats.integer(s.current().units()), s.unitsChangePct()));

        List<ReportSection> sections = new ArrayList<>();

        ReportChart chart = new ReportChart("Ventas por día (Q)",
                s.byDay().stream().map(d -> d.date().format(SHORT_DAY)).toList(),
                s.byDay().stream().map(d -> d.total()).toList(),
                "Total vendido", true);
        sections.add(new ReportSection("Ventas por día",
                List.of(ReportColumn.of("Fecha", DATE, 3), ReportColumn.of("Transacciones", INTEGER, 2),
                        ReportColumn.of("Total", MONEY, 2)),
                s.byDay().stream().map(d -> List.<Object>of(d.date(), d.transactions(), d.total())).toList(),
                List.of(), chart, true));

        sections.add(new ReportSection("Ventas por empleado",
                List.of(ReportColumn.text("Empleado", 4), ReportColumn.text("Usuario", 3),
                        ReportColumn.of("Transacciones", INTEGER, 2), ReportColumn.of("Total", MONEY, 2)),
                s.byEmployee().stream()
                        .map(e -> List.<Object>of(e.name(), e.userName(), e.transactions(), e.total())).toList(),
                List.of(), null, false));

        sections.add(new ReportSection("Productos más vendidos",
                List.of(ReportColumn.text("Producto", 6), ReportColumn.of("Unidades", INTEGER, 2),
                        ReportColumn.of("Ingresos", MONEY, 2)),
                s.topProducts().stream().map(p -> List.<Object>of(p.name(), p.units(), p.revenue())).toList(),
                List.of(), null, false));

        sections.add(new ReportSection("Detalle de ventas",
                List.of(ReportColumn.of("Fecha", DATETIME, 2.4f), ReportColumn.text("Empleado", 2),
                        ReportColumn.text("Cliente", 2.6f), ReportColumn.text("Productos", 6),
                        ReportColumn.of("Unidades", INTEGER, 1.4f), ReportColumn.of("Total", MONEY, 2)),
                s.sales().stream().map(r -> List.<Object>of(r.date(), r.employee(), r.customer(), r.products(),
                        r.units(), r.total())).toList(),
                List.of(), null, true));

        return new ReportDocument("Reporte de Ventas", subtitle, generatedBy, LocalDateTime.now(), kpis, sections);
    }

    /** Tarjeta con la variación frente al periodo anterior (o aviso si no hay base de comparación). */
    private ReportKpi kpi(String label, String value, BigDecimal changePct) {
        if (changePct == null) {
            return new ReportKpi(label, value, "Sin datos del periodo anterior", Trend.NEUTRAL);
        }
        String sign = changePct.signum() > 0 ? "+" : "";
        Trend trend = changePct.signum() > 0 ? Trend.UP : changePct.signum() < 0 ? Trend.DOWN : Trend.NEUTRAL;
        return new ReportKpi(label, value, sign + changePct.toPlainString() + "% vs periodo anterior", trend);
    }
}
