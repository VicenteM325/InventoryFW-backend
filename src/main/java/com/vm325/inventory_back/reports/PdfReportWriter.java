package com.vm325.inventory_back.reports;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static com.vm325.inventory_back.reports.ReportTheme.*;

/**
 * Exportador a PDF: hoja carta apaisada con cabecera de marca, tarjetas de indicadores y
 * tablas con anchos proporcionales, filas alternadas, texto que se ajusta en varias líneas,
 * estados en color, fila de totales y paginación ("Página X de Y"). No sabe nada de ventas
 * ni de inventario: solo dibuja un {@link ReportDocument}.
 */
@Component
public class PdfReportWriter implements ReportWriter {

    private static final PDRectangle PAGE = new PDRectangle(PDRectangle.LETTER.getHeight(), PDRectangle.LETTER.getWidth());
    private static final float MARGIN = 36f;
    private static final float CONTENT_WIDTH = PAGE.getWidth() - 2 * MARGIN;
    private static final float FIRST_BAND = 66f;
    private static final float SLIM_BAND = 28f;
    private static final float BOTTOM = 40f;
    private static final float PAD = 5f;
    private static final float LINE = 10.5f;
    private static final int MAX_LINES = 3;

    @Override
    public ReportFormat getFormat() {
        return ReportFormat.PDF;
    }

    @Override
    public byte[] write(ReportDocument document) {
        try (PDDocument pdf = new PDDocument()) {
            new Renderer(pdf, document).render();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            pdf.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Error generando el reporte PDF", e);
        }
    }

    private static final class Renderer {
        private final PDDocument pdf;
        private final ReportDocument doc;
        private final PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        private final PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        private final PDType1Font italic = new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);
        private PDPageContentStream cs;
        private float y;
        /** Ancho de la tabla en curso: las de pocas columnas no se estiran a toda la hoja. */
        private float tableW = CONTENT_WIDTH;

        Renderer(PDDocument pdf, ReportDocument doc) {
            this.pdf = pdf;
            this.doc = doc;
        }

        void render() throws IOException {
            newPage(true);
            if (!doc.kpis().isEmpty()) {
                drawKpis();
            }
            for (ReportSection section : doc.sections()) {
                drawSection(section);
            }
            closePage();
            addPageFooters();
        }

        // ---------- páginas ----------

        private void newPage(boolean first) throws IOException {
            PDPage page = new PDPage(PAGE);
            pdf.addPage(page);
            cs = new PDPageContentStream(pdf, page);
            float band = first ? FIRST_BAND : SLIM_BAND;
            fillRect(0, PAGE.getHeight() - band, PAGE.getWidth(), band, INK);
            fillRect(0, PAGE.getHeight() - band - 2.5f, PAGE.getWidth(), 2.5f, ACCENT);

            if (first) {
                text(bold, 8, MARGIN, PAGE.getHeight() - 17, new Color(0x9C, 0xA3, 0xAF), BRAND.toUpperCase());
                text(bold, 20, MARGIN, PAGE.getHeight() - 40, WHITE, doc.title());
                if (doc.subtitle() != null) {
                    text(regular, 9.5f, MARGIN, PAGE.getHeight() - 56, new Color(0xD1, 0xD5, 0xDB), doc.subtitle());
                }
                String generated = "Generado: " + doc.generatedAt().format(ReportFormats.DATE_TIME);
                textRight(regular, 8.5f, PAGE.getWidth() - MARGIN, PAGE.getHeight() - 30, new Color(0xD1, 0xD5, 0xDB), generated);
                if (doc.generatedBy() != null && !doc.generatedBy().isBlank()) {
                    textRight(regular, 8.5f, PAGE.getWidth() - MARGIN, PAGE.getHeight() - 43, new Color(0xD1, 0xD5, 0xDB),
                            "Por: " + doc.generatedBy());
                }
                y = PAGE.getHeight() - band - 22;
            } else {
                text(bold, 9.5f, MARGIN, PAGE.getHeight() - 18, WHITE, doc.title());
                textRight(regular, 8, PAGE.getWidth() - MARGIN, PAGE.getHeight() - 18, new Color(0x9C, 0xA3, 0xAF), BRAND);
                y = PAGE.getHeight() - band - 20;
            }
        }

        private void closePage() throws IOException {
            if (cs != null) {
                cs.close();
                cs = null;
            }
        }

        private void ensureSpace(float needed) throws IOException {
            if (y - needed < BOTTOM) {
                closePage();
                newPage(false);
            }
        }

        private void addPageFooters() throws IOException {
            int total = pdf.getNumberOfPages();
            for (int i = 0; i < total; i++) {
                PDPage page = pdf.getPage(i);
                try (PDPageContentStream footer = new PDPageContentStream(pdf, page, AppendMode.APPEND, true, true)) {
                    PDPageContentStream saved = cs;
                    cs = footer;
                    hRule(MARGIN, 30, CONTENT_WIDTH, BORDER, 0.6f);
                    text(regular, 7.5f, MARGIN, 19, MUTED, BRAND + "  |  " + doc.title());
                    textRight(regular, 7.5f, PAGE.getWidth() - MARGIN, 19, MUTED, "Página " + (i + 1) + " de " + total);
                    cs = saved;
                }
            }
        }

        // ---------- indicadores ----------

        private void drawKpis() throws IOException {
            int n = doc.kpis().size();
            float gap = 10f;
            float cardW = (CONTENT_WIDTH - gap * (n - 1)) / n;
            float cardH = 50f;
            ensureSpace(cardH + 14);

            for (int i = 0; i < n; i++) {
                ReportKpi kpi = doc.kpis().get(i);
                float x = MARGIN + i * (cardW + gap);
                float top = y;
                fillRect(x, top - cardH, cardW, cardH, CARD);
                hRule(x, top, cardW, BORDER, 0.6f);
                hRule(x, top - cardH, cardW, BORDER, 0.6f);
                fillRect(x, top - cardH, 3.5f, cardH, ACCENT);

                text(regular, 8, x + 12, top - 15, MUTED, kpi.label().toUpperCase());
                float valueSize = fit(bold, kpi.value(), cardW - 22, 18f, 10f);
                text(bold, valueSize, x + 12, top - 38, INK, kpi.value());
            }
            y -= cardH + 22;
        }

        /** Mayor tamaño de fuente (entre min y max) con el que el texto cabe en el ancho dado. */
        private float fit(PDType1Font font, String text, float maxWidth, float max, float min) throws IOException {
            float size = max;
            while (size > min && width(font, size, text) > maxWidth) {
                size -= 0.5f;
            }
            return size;
        }

        // ---------- secciones ----------

        private void drawSection(ReportSection section) throws IOException {
            ensureSpace(60);
            text(bold, 12, MARGIN, y, INK, section.title());
            fillRect(MARGIN, y - 6, 28, 2, ACCENT);
            y -= 22;

            drawTable(section);

            if (!section.footerLines().isEmpty()) {
                y -= 14;
                for (String line : section.footerLines()) {
                    ensureSpace(LINE + 6);
                    text(bold, 9, MARGIN, y, INK, line);
                    y -= LINE + 2;
                }
            }
            y -= 30;
        }

        // ---------- tabla ----------

        private void drawTable(ReportSection section) throws IOException {
            List<ReportColumn> cols = section.columns();
            float totalWeight = 0;
            for (ReportColumn c : cols) {
                totalWeight += c.weight();
            }
            tableW = Math.min(tableW, totalWeight * 62f);
            float[] widths = new float[cols.size()];
            for (int i = 0; i < widths.length; i++) {
                widths[i] = tableW * cols.get(i).weight() / totalWeight;
            }

            ensureSpace(22 + 24);
            drawHeaderRow(cols, widths);

            if (section.rows().isEmpty()) {
                ensureSpace(26);
                textCentered(italic, 9, MARGIN + tableW / 2, y - 16, MUTED, "Sin datos para mostrar");
                y -= 28;
                return;
            }

            int index = 0;
            for (List<Object> row : section.rows()) {
                List<List<String>> wrapped = new ArrayList<>();
                int maxLines = 1;
                for (int c = 0; c < cols.size(); c++) {
                    String text = ReportFormats.cell(row.get(c), cols.get(c).type());
                    List<String> lines = cols.get(c).type() == ColumnType.TEXT
                            ? wrap(regular, 8.5f, text, widths[c] - 2 * PAD, MAX_LINES)
                            : List.of(truncate(regular, 8.5f, text, widths[c] - 2 * PAD));
                    wrapped.add(lines);
                    maxLines = Math.max(maxLines, lines.size());
                }
                float rowH = maxLines * LINE + 7;
                if (y - rowH < BOTTOM) {
                    closePage();
                    newPage(false);
                    drawHeaderRow(cols, widths);
                }
                if (index % 2 == 1) {
                    fillRect(MARGIN, y - rowH, tableW, rowH, ZEBRA);
                }
                float x = MARGIN;
                for (int c = 0; c < cols.size(); c++) {
                    drawCell(cols.get(c), row.get(c), wrapped.get(c), x, widths[c], y);
                    x += widths[c];
                }
                y -= rowH;
                index++;
            }
            hRule(MARGIN, y, tableW, BORDER, 0.6f);

            if (section.totalsRow()) {
                drawTotals(section, cols, widths);
            }
        }

        private void drawHeaderRow(List<ReportColumn> cols, float[] widths) throws IOException {
            float h = 20f;
            fillRect(MARGIN, y - h, tableW, h, INK);
            float x = MARGIN;
            for (int c = 0; c < cols.size(); c++) {
                String header = truncate(bold, 8.5f, cols.get(c).header(), widths[c] - 2 * PAD);
                if (cols.get(c).type().isNumeric()) {
                    textRight(bold, 8.5f, x + widths[c] - PAD, y - 13.5f, WHITE, header);
                } else {
                    text(bold, 8.5f, x + PAD, y - 13.5f, WHITE, header);
                }
                x += widths[c];
            }
            y -= h;
        }

        private void drawCell(ReportColumn col, Object value, List<String> lines, float x, float w, float rowTop) throws IOException {
            if (col.type() == ColumnType.STATUS) {
                String raw = value == null ? null : value.toString();
                text(bold, 8.5f, x + PAD, rowTop - 12, statusColor(raw), lines.get(0));
                return;
            }
            for (int i = 0; i < lines.size(); i++) {
                float ly = rowTop - 12 - i * LINE;
                if (col.type().isNumeric()) {
                    textRight(regular, 8.5f, x + w - PAD, ly, INK, lines.get(i));
                } else {
                    text(regular, 8.5f, x + PAD, ly, INK, lines.get(i));
                }
            }
        }

        private void drawTotals(ReportSection section, List<ReportColumn> cols, float[] widths) throws IOException {
            float h = 22f;
            ensureSpace(h + 4);
            fillRect(MARGIN, y - h, tableW, h, new Color(0xE5, 0xE7, 0xEB));
            float x = MARGIN;
            for (int c = 0; c < cols.size(); c++) {
                ReportColumn col = cols.get(c);
                if (c == 0) {
                    text(bold, 9, x + PAD, y - 14.5f, INK, "Total");
                } else if (col.type().isNumeric()) {
                    BigDecimal sum = BigDecimal.ZERO;
                    for (List<Object> row : section.rows()) {
                        if (row.get(c) != null) {
                            sum = sum.add(new BigDecimal(row.get(c).toString()));
                        }
                    }
                    String formatted = col.type() == ColumnType.MONEY ? ReportFormats.money(sum) : ReportFormats.integer(sum);
                    textRight(bold, 9, x + widths[c] - PAD, y - 14.5f, INK, formatted);
                }
                x += widths[c];
            }
            y -= h;
        }

        // ---------- texto y formas ----------

        private List<String> wrap(PDType1Font font, float size, String raw, float maxWidth, int maxLines) throws IOException {
            String text = sanitize(raw);
            List<String> lines = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            for (String word : text.split(" ")) {
                for (String piece : breakLongWord(font, size, word, maxWidth)) {
                    String candidate = current.isEmpty() ? piece : current + " " + piece;
                    if (width(font, size, candidate) <= maxWidth) {
                        current = new StringBuilder(candidate);
                    } else {
                        lines.add(current.toString());
                        current = new StringBuilder(piece);
                    }
                }
            }
            lines.add(current.toString());
            if (lines.size() > maxLines) {
                List<String> cut = new ArrayList<>(lines.subList(0, maxLines));
                cut.set(maxLines - 1, truncate(font, size, cut.get(maxLines - 1) + " ...", maxWidth));
                return cut;
            }
            return lines;
        }

        private List<String> breakLongWord(PDType1Font font, float size, String word, float maxWidth) throws IOException {
            if (width(font, size, word) <= maxWidth) {
                return List.of(word);
            }
            List<String> parts = new ArrayList<>();
            StringBuilder part = new StringBuilder();
            for (char ch : word.toCharArray()) {
                if (width(font, size, part.toString() + ch) > maxWidth && !part.isEmpty()) {
                    parts.add(part.toString());
                    part = new StringBuilder();
                }
                part.append(ch);
            }
            parts.add(part.toString());
            return parts;
        }

        private String truncate(PDType1Font font, float size, String raw, float maxWidth) throws IOException {
            String text = sanitize(raw);
            if (width(font, size, text) <= maxWidth) {
                return text;
            }
            StringBuilder sb = new StringBuilder();
            for (char ch : text.toCharArray()) {
                if (width(font, size, sb.toString() + ch + "...") > maxWidth) {
                    break;
                }
                sb.append(ch);
            }
            return sb + "...";
        }

        private float width(PDType1Font font, float size, String text) throws IOException {
            return font.getStringWidth(sanitize(text)) / 1000f * size;
        }

        private void text(PDType1Font font, float size, float x, float baseline, Color color, String value) throws IOException {
            cs.beginText();
            cs.setFont(font, size);
            cs.setNonStrokingColor(color);
            cs.newLineAtOffset(x, baseline);
            cs.showText(sanitize(value));
            cs.endText();
        }

        private void textRight(PDType1Font font, float size, float xRight, float baseline, Color color, String value) throws IOException {
            text(font, size, xRight - width(font, size, value), baseline, color, value);
        }

        private void textCentered(PDType1Font font, float size, float xCenter, float baseline, Color color, String value) throws IOException {
            text(font, size, xCenter - width(font, size, value) / 2, baseline, color, value);
        }

        private void fillRect(float x, float yBottom, float w, float h, Color color) throws IOException {
            cs.setNonStrokingColor(color);
            cs.addRect(x, yBottom, w, h);
            cs.fill();
        }

        private void hRule(float x, float yPos, float w, Color color, float lineWidth) throws IOException {
            cs.setStrokingColor(color);
            cs.setLineWidth(lineWidth);
            cs.moveTo(x, yPos);
            cs.lineTo(x + w, yPos);
            cs.stroke();
        }

        // Las fuentes estándar de PDF (WinAnsi) no codifican todo Unicode: se sustituye lo
        // que no entra en Latin-1 imprimible para que showText nunca falle por un carácter raro.
        private static String sanitize(String text) {
            if (text == null) {
                return "";
            }
            StringBuilder sb = new StringBuilder(text.length());
            text.codePoints().forEach(cp -> {
                if (cp < 0x20 || (cp >= 0x7F && cp <= 0x9F)) {
                    sb.append(' ');
                } else {
                    sb.append(cp <= 0xFF ? (char) cp : '?');
                }
            });
            return sb.toString();
        }
    }
}
