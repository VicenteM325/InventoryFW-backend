package com.vm325.inventory_back.reports;

import com.vm325.inventory_back.reports.ReportKpi.Trend;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.vm325.inventory_back.reports.ColumnType.*;
import static org.junit.jupiter.api.Assertions.*;

class ReportWritersTest {

    private final PdfReportWriter pdf = new PdfReportWriter();
    private final XlsxReportWriter xlsx = new XlsxReportWriter();

    static ReportDocument sampleDocument(int saleRows) {
        List<List<Object>> days = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        List<BigDecimal> values = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            LocalDate day = LocalDate.of(2026, 3, 1).plusDays(i);
            BigDecimal total = i % 7 == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(300 + (i * 137) % 900);
            days.add(List.of(day, i % 7 == 0 ? 0 : 2 + i % 4, total));
            labels.add(String.format("%02d/03", i + 1));
            values.add(total);
        }

        List<List<Object>> sales = new ArrayList<>();
        for (int i = 0; i < saleRows; i++) {
            sales.add(List.of(LocalDateTime.of(2026, 3, 1 + i % 28, 9 + i % 9, i % 60), "empleado" + i % 3,
                    i % 5 == 0 ? "-" : "Cliente Ñandú " + i,
                    "Celular Samsung Galaxy A54 128GB Negro x1, Funda protectora transparente x2, Cargador rápido USB-C 25W x1",
                    4, BigDecimal.valueOf(1234.5 + i)));
        }

        ReportChart chart = new ReportChart("Ventas por día (Q)", labels, values, "Total vendido", true);
        List<ReportSection> sections = List.of(
                new ReportSection("Ventas por día",
                        List.of(ReportColumn.of("Fecha", DATE, 3), ReportColumn.of("Transacciones", INTEGER, 2),
                                ReportColumn.of("Total", MONEY, 2)),
                        days, List.of("Mejor día: 15/03"), chart, true),
                new ReportSection("Productos más vendidos",
                        List.of(ReportColumn.text("Producto", 6), ReportColumn.of("Unidades", INTEGER, 2),
                                ReportColumn.of("Ingresos", MONEY, 2)),
                        List.of(), List.of(), null, false),
                new ReportSection("Alertas",
                        List.of(ReportColumn.text("Producto", 4), ReportColumn.of("Estado", STATUS, 2),
                                ReportColumn.of("Atendida", DATETIME, 2)),
                        List.of(Arrays.<Object>asList("Cámara € 😀", "PENDIENTE", null),
                                Arrays.<Object>asList("Cable", "ATENDIDA", LocalDateTime.of(2026, 3, 4, 10, 30))),
                        List.of(), null, false),
                new ReportSection("Detalle de ventas",
                        List.of(ReportColumn.of("Fecha", DATETIME, 2.4f), ReportColumn.text("Empleado", 2),
                                ReportColumn.text("Cliente", 2.6f), ReportColumn.text("Productos", 6),
                                ReportColumn.of("Unidades", INTEGER, 1.4f), ReportColumn.of("Total", MONEY, 2)),
                        sales, List.of(), null, true));

        return new ReportDocument("Reporte de Ventas", "Periodo: 01/03/2026 al 30/03/2026", "admin",
                LocalDateTime.of(2026, 3, 31, 8, 15),
                List.of(new ReportKpi("Total vendido", "Q 18,450.00", "+12.5% vs periodo anterior", Trend.UP),
                        new ReportKpi("Transacciones", "72", "-3.1% vs periodo anterior", Trend.DOWN),
                        ReportKpi.of("Ticket promedio", "Q 256.25"),
                        new ReportKpi("Unidades vendidas", "140", "Sin datos del periodo anterior", Trend.NEUTRAL)),
                sections);
    }

    private static String text(byte[] pdfBytes) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            return new PDFTextStripper().getText(doc);
        }
    }

    @Test
    void pdfIsValidAndPaginatesWithPageNumbers() throws IOException {
        byte[] bytes = pdf.write(sampleDocument(120));

        try (PDDocument doc = Loader.loadPDF(bytes)) {
            int pages = doc.getNumberOfPages();
            assertTrue(pages > 2, "120 ventas deben ocupar varias páginas, hubo " + pages);
            String all = new PDFTextStripper().getText(doc);
            assertTrue(all.contains("Página 1 de " + pages));
            assertTrue(all.contains("Página " + pages + " de " + pages));
        }
        maybeDump("ventas.pdf", bytes);
    }

    @Test
    void pdfContainsHeaderKpisSectionsAndTotals() throws IOException {
        String content = text(pdf.write(sampleDocument(5)));

        assertTrue(content.contains("Reporte de Ventas"));
        assertTrue(content.contains("Periodo: 01/03/2026 al 30/03/2026"));
        assertTrue(content.contains("Por: admin"));
        assertTrue(content.contains("Q 18,450.00"));
        assertTrue(content.contains("+12.5% vs periodo anterior"));
        assertTrue(content.contains("Productos más vendidos"));
        assertTrue(content.contains("Total"));
        assertTrue(content.contains("Pendiente"), "los estados se muestran legibles");
    }

    @Test
    void pdfShowsPlaceholderForEmptySectionAndSurvivesUnencodableCharacters() throws IOException {
        String content = text(pdf.write(sampleDocument(2)));

        assertTrue(content.contains("Sin datos para mostrar"));
        assertTrue(content.contains("C"), "el texto con euro y emoji no debe romper la generación");
    }

    @Test
    void pdfTotalsRowSumsNumericColumns() throws IOException {
        // 2 ventas: 1234.5 + 1235.5 = 2470.00
        String content = text(pdf.write(sampleDocument(2)));

        assertTrue(content.contains("Q 2,470.00"), content);
    }

    @Test
    void xlsxHasSummaryAndOneSheetPerSection() throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx.write(sampleDocument(10))))) {
            assertEquals(List.of("Resumen", "Ventas por día", "Productos más vendidos", "Alertas", "Detalle de ventas"),
                    names(wb));
            XSSFSheet summary = wb.getSheet("Resumen");
            assertEquals("Reporte de Ventas", summary.getRow(0).getCell(0).getStringCellValue());
            assertEquals("TOTAL VENDIDO", summary.getRow(3).getCell(0).getStringCellValue());
            assertEquals("Q 18,450.00", summary.getRow(4).getCell(0).getStringCellValue());
            assertEquals(1, summary.getDrawingPatriarch().getCharts().size());
        }
    }

    private static List<String> names(XSSFWorkbook wb) {
        List<String> names = new ArrayList<>();
        wb.forEach(s -> names.add(s.getSheetName()));
        return names;
    }

    @Test
    void xlsxStoresNativeTypesWithFiltersFreezeAndTotals() throws IOException {
        byte[] bytes = xlsx.write(sampleDocument(10));
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            XSSFSheet detail = wb.getSheet("Detalle de ventas");

            assertEquals("Fecha", detail.getRow(3).getCell(0).getStringCellValue());
            assertEquals(CellType.NUMERIC, detail.getRow(4).getCell(0).getCellType());
            assertTrue(DateUtil.isCellDateFormatted(detail.getRow(4).getCell(0)), "las fechas son fechas, no texto");
            assertEquals(CellType.NUMERIC, detail.getRow(4).getCell(5).getCellType());
            assertEquals(1234.5, detail.getRow(4).getCell(5).getNumericCellValue(), 0.001);

            assertNotNull(detail.getCTWorksheet().getAutoFilter(), "tiene autofiltro");
            assertEquals(4, detail.getPaneInformation().getHorizontalSplitPosition(), "encabezado congelado");

            // 10 filas de datos (filas 4..13) y la de totales en la 14
            var totals = detail.getRow(14);
            assertEquals("Total", totals.getCell(0).getStringCellValue());
            assertEquals("SUM(F5:F14)", totals.getCell(5).getCellFormula());
            assertEquals(40.0, totals.getCell(4).getNumericCellValue(), 0.001);
            double expected = 10 * 1234.5 + 45;
            assertEquals(expected, totals.getCell(5).getNumericCellValue(), 0.001);
        }
        maybeDump("ventas.xlsx", bytes);
    }

    @Test
    void xlsxHandlesNullsAndEmptySections() throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx.write(sampleDocument(1))))) {
            assertEquals("Sin datos para mostrar", wb.getSheet("Productos más vendidos").getRow(4).getCell(0).getStringCellValue());
            assertEquals("-", wb.getSheet("Alertas").getRow(4).getCell(2).getStringCellValue());
            assertNull(wb.getSheet("Productos más vendidos").getCTWorksheet().getAutoFilter());
        }
    }

    @Test
    void sheetNamesAreUniqueAndValid() throws IOException {
        ReportSection s = new ReportSection("Detalle: ventas/día [1]?", List.of(ReportColumn.text("A", 1)), List.of(), List.of(), null, false);
        ReportDocument doc = new ReportDocument("T", null, null, LocalDateTime.now(), List.of(), List.of(s, s));

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx.write(doc)))) {
            assertEquals(2, wb.getNumberOfSheets());
            assertNotEquals(wb.getSheetName(0), wb.getSheetName(1));
        }
    }

    /** Con -Dreport.dump.dir=... deja copias de los archivos para revisarlos a ojo. */
    private static void maybeDump(String name, byte[] bytes) throws IOException {
        String dir = System.getProperty("report.dump.dir");
        if (dir != null) {
            Files.write(Path.of(dir, name), bytes);
        }
    }

    @Test
    void countChartAxisUsesWholeNumberSteps() throws IOException {
        // Sin esto, 8 productos daban un eje 0 / 2.5 / 5 / 7.5 / 10: no existen 2.5 productos.
        ReportChart chart = new ReportChart("Productos por estado", List.of("A", "B", "C"),
                List.of(BigDecimal.valueOf(8), BigDecimal.ONE, BigDecimal.ONE), "Productos", false);
        ReportSection section = new ReportSection("Estado", List.of(ReportColumn.text("Producto", 1)),
                List.of(), List.of(), chart, false);
        ReportDocument doc = new ReportDocument("T", null, "admin", LocalDateTime.now(), List.of(), List.of(section));

        String content = text(pdf.write(doc));

        assertFalse(content.contains("2.5"), content);
        assertFalse(content.contains("7.5"), content);
        assertTrue(content.contains("10"));
    }
}
