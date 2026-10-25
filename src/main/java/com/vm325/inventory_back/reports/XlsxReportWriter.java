package com.vm325.inventory_back.reports;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
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
 * Exportador a .xlsx: una hoja "Resumen" con los indicadores y una hoja por sección, con
 * encabezado de marca, valores en su tipo nativo (números, moneda, fechas) para poder
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

            XSSFSheet summary = null;
            if (!document.kpis().isEmpty()) {
                summary = workbook.createSheet(uniqueName("Resumen", usedNames));
            }

            for (ReportSection section : document.sections()) {
                XSSFSheet sheet = workbook.createSheet(uniqueName(section.title(), usedNames));
                writeSection(sheet, document, section, styles);
            }

            if (summary != null) {
                writeSummary(summary, document, styles);
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

        titleBlock(sheet, section.title(), doc, lastCol, st);

        Row header = sheet.createRow(HEADER_ROW);
        header.setHeightInPoints(20);
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

    private void writeSummary(XSSFSheet sheet, ReportDocument doc, Styles st) {
        sheet.setDisplayGridlines(false);
        titleBlock(sheet, doc.title(), doc, 1, st);

        Row header = sheet.createRow(HEADER_ROW);
        header.setHeightInPoints(20);
        Cell concept = header.createCell(0);
        concept.setCellValue("Concepto");
        concept.setCellStyle(st.header(false));
        Cell value = header.createCell(1);
        value.setCellValue("Valor");
        value.setCellStyle(st.header(true));

        int rowIndex = HEADER_ROW + 1;
        for (ReportKpi kpi : doc.kpis()) {
            Row row = sheet.createRow(rowIndex++);
            Cell label = row.createCell(0);
            label.setCellValue(kpi.label());
            label.setCellStyle(st.data(ColumnType.TEXT, false, null));
            Cell number = row.createCell(1);
            number.setCellValue(kpi.value());
            number.setCellStyle(st.data(ColumnType.INTEGER, false, null));
        }
        sheet.setColumnWidth(0, 30 * 256);
        sheet.setColumnWidth(1, 20 * 256);
        printSetup(sheet, HEADER_ROW);
    }

    // ---------- comunes ----------

    private void titleBlock(XSSFSheet sheet, String title, ReportDocument doc, int lastColumn, Styles st) {
        Row titleRow = sheet.createRow(0);
        titleRow.setHeightInPoints(30);
        int lastCol = Math.max(lastColumn, 1);
        for (int c = 0; c <= lastCol; c++) {
            titleRow.createCell(c).setCellStyle(st.title());
        }
        titleRow.getCell(0).setCellValue(title);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, lastCol));

        Cell sub = sheet.createRow(1).createCell(0);
        sub.setCellValue(doc.subtitle() == null ? BRAND : BRAND + "  |  " + doc.subtitle());
        sub.setCellStyle(st.muted());

        String generated = "Generado: " + doc.generatedAt().format(ReportFormats.DATE_TIME)
                + (doc.generatedBy() == null || doc.generatedBy().isBlank() ? "" : "  |  Por: " + doc.generatedBy());
        Cell gen = sheet.createRow(2).createCell(0);
        gen.setCellValue(generated);
        gen.setCellStyle(st.muted());
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
