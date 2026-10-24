package com.vm325.inventory_back.reports;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xddf.usermodel.XDDFColor;
import org.apache.poi.xddf.usermodel.XDDFSolidFillProperties;
import org.apache.poi.xddf.usermodel.chart.AxisCrosses;
import org.apache.poi.xddf.usermodel.chart.AxisPosition;
import org.apache.poi.xddf.usermodel.chart.BarDirection;
import org.apache.poi.xddf.usermodel.chart.ChartTypes;
import org.apache.poi.xddf.usermodel.chart.XDDFBarChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFCategoryAxis;
import org.apache.poi.xddf.usermodel.chart.XDDFDataSource;
import org.apache.poi.xddf.usermodel.chart.XDDFDataSourcesFactory;
import org.apache.poi.xddf.usermodel.chart.XDDFNumericalDataSource;
import org.apache.poi.xddf.usermodel.chart.XDDFValueAxis;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFChart;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFFormulaEvaluator;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.vm325.inventory_back.reports.ReportTheme.*;

/**
 * Exportador a .xlsx: una hoja "Resumen" (indicadores y gráficos) y una hoja por sección con
 * encabezado de marca, valores con su tipo nativo (números, moneda, fechas) para poder
 * ordenar y calcular, filas alternadas, filtros, panel fijo, fila de totales con fórmulas
 * SUM y configuración de impresión apaisada. No sabe nada de ventas ni de inventario.
 */
@Component
public class XlsxReportWriter implements ReportWriter {

    private static final int HEADER_ROW = 3;

    @Override
    public ReportFormat getFormat() {
        return ReportFormat.XLSX;
    }

    @Override
    public byte[] write(ReportDocument document) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Styles styles = new Styles(workbook);
            Set<String> usedNames = new HashSet<>();

            boolean hasSummary = !document.kpis().isEmpty()
                    || document.sections().stream().anyMatch(s -> s.chart() != null);
            XSSFSheet summary = null;
            if (hasSummary) {
                summary = workbook.createSheet(uniqueName("Resumen", usedNames));
            }

            for (ReportSection section : document.sections()) {
                XSSFSheet sheet = workbook.createSheet(uniqueName(section.title(), usedNames));
                writeSection(sheet, document, section, styles);
            }

            if (summary != null) {
                writeSummary(workbook, summary, document, styles);
            }

            // POI no calcula las fórmulas: se evalúan para que los totales también se vean
            // en visores que no recalculan, y Excel igualmente las recalcula al abrir.
            XSSFFormulaEvaluator.evaluateAllFormulaCells(workbook);
            workbook.setForceFormulaRecalculation(true);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Error generando el archivo Excel", e);
        }
    }

    // ---------- hoja de sección ----------

    private void writeSection(XSSFSheet sheet, ReportDocument doc, ReportSection section, Styles st) {
        List<ReportColumn> cols = section.columns();
        int lastCol = cols.size() - 1;
        sheet.setDisplayGridlines(false);

        titleBlock(sheet, section.title(), doc.subtitle(), lastCol, st);

        Row header = sheet.createRow(HEADER_ROW);
        header.setHeightInPoints(22);
        for (int c = 0; c < cols.size(); c++) {
            Cell cell = header.createCell(c);
            cell.setCellValue(cols.get(c).header());
            cell.setCellStyle(st.header(cols.get(c).type().isNumeric()));
        }

        int first = HEADER_ROW + 1;
        int rowIndex = first;
        for (List<Object> values : section.rows()) {
            Row row = sheet.createRow(rowIndex);
            boolean zebra = (rowIndex - first) % 2 == 1;
            for (int c = 0; c < cols.size(); c++) {
                writeCell(row.createCell(c), cols.get(c), values.get(c), zebra, st);
            }
            rowIndex++;
        }
        int last = rowIndex - 1;

        if (section.rows().isEmpty()) {
            Row empty = sheet.createRow(rowIndex++);
            Cell cell = empty.createCell(0);
            cell.setCellValue("Sin datos para mostrar");
            cell.setCellStyle(st.muted());
        } else {
            sheet.setAutoFilter(new CellRangeAddress(HEADER_ROW, last, 0, lastCol));
            if (section.totalsRow()) {
                rowIndex = writeTotals(sheet, cols, first, last, rowIndex, st);
            }
        }

        if (!section.footerLines().isEmpty()) {
            rowIndex++;
            for (String line : section.footerLines()) {
                Cell cell = sheet.createRow(rowIndex++).createCell(0);
                cell.setCellValue(line);
                cell.setCellStyle(st.bold());
            }
        }

        sheet.createFreezePane(0, HEADER_ROW + 1);
        for (int c = 0; c < cols.size(); c++) {
            sheet.setColumnWidth(c, columnWidth(cols.get(c), section.rows(), c));
        }
        printSetup(sheet, HEADER_ROW);
    }

    private int writeTotals(XSSFSheet sheet, List<ReportColumn> cols, int first, int last, int rowIndex, Styles st) {
        Row row = sheet.createRow(rowIndex);
        row.setHeightInPoints(20);
        for (int c = 0; c < cols.size(); c++) {
            Cell cell = row.createCell(c);
            ReportColumn col = cols.get(c);
            if (c == 0) {
                cell.setCellValue("Total");
                cell.setCellStyle(st.total(ColumnType.TEXT));
            } else if (col.type().isNumeric()) {
                String range = new CellRangeAddress(first, last, c, c).formatAsString();
                cell.setCellFormula("SUM(" + range + ")");
                cell.setCellStyle(st.total(col.type()));
            } else {
                cell.setCellStyle(st.total(ColumnType.TEXT));
            }
        }
        return rowIndex + 1;
    }

    private void writeCell(Cell cell, ReportColumn col, Object value, boolean zebra, Styles st) {
        if (value == null) {
            cell.setCellValue("-");
            cell.setCellStyle(st.data(ColumnType.TEXT, zebra, null));
            return;
        }
        switch (col.type()) {
            case INTEGER -> cell.setCellValue(((Number) value).doubleValue());
            case MONEY -> cell.setCellValue(new java.math.BigDecimal(value.toString()).doubleValue());
            case DATE -> cell.setCellValue((LocalDate) value);
            case DATETIME -> cell.setCellValue((LocalDateTime) value);
            case STATUS -> cell.setCellValue(ReportFormats.status(value.toString()));
            default -> cell.setCellValue(value.toString());
        }
        cell.setCellStyle(st.data(col.type(), zebra, col.type() == ColumnType.STATUS ? value.toString() : null));
    }

    /** Ancho en unidades de Excel: el del contenido más largo, acotado a un rango legible. */
    private int columnWidth(ReportColumn col, List<List<Object>> rows, int index) {
        int longest = col.header().length() + 3;
        for (List<Object> row : rows) {
            longest = Math.max(longest, ReportFormats.cell(row.get(index), col.type()).length());
        }
        int min = col.type() == ColumnType.TEXT ? 12 : 11;
        int max = col.type() == ColumnType.TEXT ? 55 : 24;
        return Math.min(Math.max(longest + 2, min), max) * 256;
    }

    // ---------- hoja de resumen ----------

    private void writeSummary(XSSFWorkbook workbook, XSSFSheet sheet, ReportDocument doc, Styles st) {
        sheet.setDisplayGridlines(false);
        int kpis = doc.kpis().size();
        int lastCol = Math.max(kpis * 2, 8) - 1;

        titleBlock(sheet, doc.title(), doc.subtitle(), lastCol, st);
        for (int c = 0; c <= lastCol; c++) {
            sheet.setColumnWidth(c, 14 * 256);
        }

        // Tarjetas de indicadores: cada una ocupa dos columnas y tres filas (etiqueta, valor, nota).
        Row labels = sheet.createRow(3);
        Row values = sheet.createRow(4);
        Row notes = sheet.createRow(5);
        values.setHeightInPoints(30);
        for (int i = 0; i < kpis; i++) {
            ReportKpi kpi = doc.kpis().get(i);
            int c = i * 2;
            card(sheet, labels, 3, c, kpi.label().toUpperCase(), st.kpiLabel());
            card(sheet, values, 4, c, kpi.value(), st.kpiValue());
            card(sheet, notes, 5, c, kpi.note() == null ? "" : kpi.note(), st.kpiNote(kpi.trend()));
        }

        int cursor = 7;
        for (ReportSection section : doc.sections()) {
            ReportChart chart = section.chart();
            if (chart == null || chart.labels().isEmpty()) {
                continue;
            }
            cursor = addChart(sheet, chart, cursor, lastCol, st);
        }

        printSetup(sheet, -1);
    }

    private void card(XSSFSheet sheet, Row row, int rowIndex, int col, String text, XSSFCellStyle style) {
        Cell first = row.createCell(col);
        first.setCellValue(text);
        first.setCellStyle(style);
        Cell second = row.createCell(col + 1);
        second.setCellStyle(style);
        sheet.addMergedRegion(new CellRangeAddress(rowIndex, rowIndex, col, col + 1));
    }

    /** Dibuja el gráfico y debajo su tabla de datos (el gráfico se alimenta de ahí). Devuelve la fila libre siguiente. */
    private int addChart(XSSFSheet sheet, ReportChart chart, int startRow, int lastCol, Styles st) {
        int chartRows = 18;
        int dataTitleRow = startRow + chartRows + 1;
        int dataFirst = dataTitleRow + 2;
        int n = chart.labels().size();
        int dataLast = dataFirst + n - 1;

        Cell dataTitle = sheet.createRow(dataTitleRow).createCell(0);
        dataTitle.setCellValue("Datos del gráfico: " + chart.title());
        dataTitle.setCellStyle(st.bold());

        Row head = sheet.createRow(dataTitleRow + 1);
        Cell h0 = head.createCell(0);
        h0.setCellValue("Etiqueta");
        h0.setCellStyle(st.header(false));
        Cell h1 = head.createCell(1);
        h1.setCellValue(chart.seriesName());
        h1.setCellStyle(st.header(true));

        for (int i = 0; i < n; i++) {
            Row row = sheet.createRow(dataFirst + i);
            boolean zebra = i % 2 == 1;
            Cell label = row.createCell(0);
            label.setCellValue(chart.labels().get(i));
            label.setCellStyle(st.data(ColumnType.TEXT, zebra, null));
            Cell value = row.createCell(1);
            value.setCellValue(chart.values().get(i).doubleValue());
            value.setCellStyle(st.data(chart.money() ? ColumnType.MONEY : ColumnType.INTEGER, zebra, null));
        }

        XSSFDrawing drawing = sheet.createDrawingPatriarch();
        XSSFClientAnchor anchor = drawing.createAnchor(0, 0, 0, 0, 0, startRow, lastCol + 1, startRow + chartRows);
        XSSFChart xssfChart = drawing.createChart(anchor);
        xssfChart.setTitleText(chart.title());
        xssfChart.setTitleOverlay(false);

        XDDFCategoryAxis bottom = xssfChart.createCategoryAxis(AxisPosition.BOTTOM);
        XDDFValueAxis left = xssfChart.createValueAxis(AxisPosition.LEFT);
        left.setCrosses(AxisCrosses.AUTO_ZERO);

        XDDFDataSource<String> categories = XDDFDataSourcesFactory.fromStringCellRange(sheet,
                new CellRangeAddress(dataFirst, dataLast, 0, 0));
        XDDFNumericalDataSource<Double> numbers = XDDFDataSourcesFactory.fromNumericCellRange(sheet,
                new CellRangeAddress(dataFirst, dataLast, 1, 1));

        XDDFBarChartData data = (XDDFBarChartData) xssfChart.createData(ChartTypes.BAR, bottom, left);
        data.setBarDirection(BarDirection.COL);
        XDDFBarChartData.Series series = (XDDFBarChartData.Series) data.addSeries(categories, numbers);
        series.setTitle(chart.seriesName(), new CellReference(sheet.getSheetName(), dataTitleRow + 1, 1, true, true));
        series.setFillProperties(new XDDFSolidFillProperties(XDDFColor.from(new byte[]{
                (byte) ACCENT.getRed(), (byte) ACCENT.getGreen(), (byte) ACCENT.getBlue()})));
        xssfChart.plot(data);

        return dataLast + 3;
    }

    // ---------- comunes ----------

    private void titleBlock(XSSFSheet sheet, String title, String subtitle, int lastCol, Styles st) {
        Row titleRow = sheet.createRow(0);
        titleRow.setHeightInPoints(30);
        for (int c = 0; c <= lastCol; c++) {
            titleRow.createCell(c).setCellStyle(st.title());
        }
        titleRow.getCell(0).setCellValue(title);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, Math.max(lastCol, 1)));

        Row sub = sheet.createRow(1);
        Cell cell = sub.createCell(0);
        cell.setCellValue(subtitle == null ? BRAND : BRAND + "  |  " + subtitle);
        cell.setCellStyle(st.muted());
    }

    private void printSetup(XSSFSheet sheet, int repeatHeaderRow) {
        sheet.getPrintSetup().setLandscape(true);
        sheet.setFitToPage(true);
        sheet.getPrintSetup().setFitWidth((short) 1);
        sheet.getPrintSetup().setFitHeight((short) 0);
        sheet.setHorizontallyCenter(true);
        sheet.getFooter().setLeft(BRAND);
        sheet.getFooter().setRight("Página &P de &N");
        if (repeatHeaderRow >= 0) {
            sheet.setRepeatingRows(new CellRangeAddress(repeatHeaderRow, repeatHeaderRow, -1, -1));
        }
    }

    private String uniqueName(String title, Set<String> used) {
        String base = title.replaceAll("[\\[\\]:*?/\\\\]", " ").trim();
        if (base.length() > 28) {
            base = base.substring(0, 28).trim();
        }
        String name = base;
        int suffix = 2;
        while (!used.add(name.toLowerCase())) {
            name = base + " " + suffix++;
        }
        return name;
    }

    /** Estilos de celda reutilizados (Excel limita a ~64 000 estilos por libro, así que se crean una sola vez). */
    private static final class Styles {
        private final XSSFWorkbook wb;
        private final Map<String, XSSFCellStyle> cache = new HashMap<>();

        Styles(XSSFWorkbook wb) {
            this.wb = wb;
        }

        XSSFCellStyle title() {
            return cache.computeIfAbsent("title", k -> {
                XSSFCellStyle s = base();
                s.setFont(font(16, true, WHITE));
                fill(s, INK);
                s.setVerticalAlignment(VerticalAlignment.CENTER);
                return s;
            });
        }

        XSSFCellStyle muted() {
            return cache.computeIfAbsent("muted", k -> {
                XSSFCellStyle s = base();
                s.setFont(font(9, false, MUTED));
                return s;
            });
        }

        XSSFCellStyle bold() {
            return cache.computeIfAbsent("bold", k -> {
                XSSFCellStyle s = base();
                s.setFont(font(10, true, INK));
                return s;
            });
        }

        XSSFCellStyle header(boolean right) {
            return cache.computeIfAbsent("header" + right, k -> {
                XSSFCellStyle s = base();
                s.setFont(font(10, true, WHITE));
                fill(s, INK);
                s.setAlignment(right ? HorizontalAlignment.RIGHT : HorizontalAlignment.LEFT);
                s.setVerticalAlignment(VerticalAlignment.CENTER);
                s.setIndention((short) 1);
                return s;
            });
        }

        XSSFCellStyle data(ColumnType type, boolean zebra, String status) {
            return cache.computeIfAbsent("data" + type + zebra + status, k -> {
                XSSFCellStyle s = base();
                s.setFont(status != null ? font(10, true, statusColor(status)) : font(10, false, INK));
                if (zebra) {
                    fill(s, ZEBRA);
                }
                border(s, BorderStyle.THIN, BORDER);
                s.setVerticalAlignment(VerticalAlignment.TOP);
                s.setAlignment(type.isNumeric() ? HorizontalAlignment.RIGHT : HorizontalAlignment.LEFT);
                s.setWrapText(type == ColumnType.TEXT);
                format(s, type);
                return s;
            });
        }

        XSSFCellStyle total(ColumnType type) {
            return cache.computeIfAbsent("total" + type, k -> {
                XSSFCellStyle s = base();
                s.setFont(font(10, true, INK));
                fill(s, new Color(0xE5, 0xE7, 0xEB));
                s.setBorderTop(BorderStyle.MEDIUM);
                s.setTopBorderColor(new XSSFColor(INK, null));
                s.setAlignment(type.isNumeric() ? HorizontalAlignment.RIGHT : HorizontalAlignment.LEFT);
                s.setVerticalAlignment(VerticalAlignment.CENTER);
                format(s, type);
                return s;
            });
        }

        XSSFCellStyle kpiLabel() {
            return cache.computeIfAbsent("kpiLabel", k -> kpiBase(font(8, true, MUTED), true));
        }

        XSSFCellStyle kpiValue() {
            return cache.computeIfAbsent("kpiValue", k -> kpiBase(font(18, true, INK), false));
        }

        XSSFCellStyle kpiNote(ReportKpi.Trend trend) {
            return cache.computeIfAbsent("kpiNote" + trend, k -> kpiBase(font(9, true, trendColor(trend)), false));
        }

        private XSSFCellStyle kpiBase(XSSFFont font, boolean top) {
            XSSFCellStyle s = base();
            s.setFont(font);
            fill(s, CARD);
            s.setAlignment(HorizontalAlignment.LEFT);
            s.setVerticalAlignment(VerticalAlignment.CENTER);
            s.setIndention((short) 1);
            return s;
        }

        private XSSFCellStyle base() {
            return wb.createCellStyle();
        }

        private void fill(XSSFCellStyle s, Color color) {
            s.setFillForegroundColor(new XSSFColor(color, null));
            s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }

        private void border(XSSFCellStyle s, BorderStyle style, Color color) {
            XSSFColor c = new XSSFColor(color, null);
            s.setBorderBottom(style);
            s.setBottomBorderColor(c);
        }

        private void format(XSSFCellStyle s, ColumnType type) {
            String pattern = switch (type) {
                case MONEY -> "\"Q\" #,##0.00";
                case INTEGER -> "#,##0";
                case DATE -> "dd/mm/yyyy";
                case DATETIME -> "dd/mm/yyyy hh:mm";
                default -> null;
            };
            if (pattern != null) {
                s.setDataFormat(wb.createDataFormat().getFormat(pattern));
            }
        }

        private XSSFFont font(int size, boolean bold, Color color) {
            XSSFFont f = wb.createFont();
            f.setFontHeightInPoints((short) size);
            f.setBold(bold);
            f.setColor(new XSSFColor(color, null));
            return f;
        }
    }
}
