package com.vm325.inventory_back.imaging;

import com.vm325.inventory_back.imaging.PhotoSheetSpec.SheetGrid;
import com.vm325.inventory_back.imaging.PhotoSheetSpec.SheetOrientation;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.xwpf.usermodel.Document;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.TableRowAlign;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTable.XWPFBorderType;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBorder;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTParaRPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSpacing;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblCellMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblGrid;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblWidth;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcBorders;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STPageOrientation;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblLayoutType;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblWidth;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigInteger;

/**
 * Genera la hoja de fotos en Word (.docx): una tabla de celdas de tamaño
 * fijo, una por copia, en una página del tamaño de la hoja de impresión
 * (p.ej. 4x6 in). La foto se dibuja a su tamaño real en el centro de su
 * celda. A diferencia del PDF no hay una separación configurable entre
 * copias: la separación entre celdas de Word sumaría n+1 espacios y
 * desbordaría el ancho de la cédula, así que cada celda solo deja ~1 pt de
 * holgura (el grosor del borde) y las copias contiguas comparten su línea
 * gris de corte.
 */
@Component
public class WordPhotoSheetComposer implements PhotoSheetComposer {

    private static final int EMU_PER_POINT = 12700; // 914400 EMU/in ÷ 72 pt/in
    private static final int TWIPS_PER_POINT = 20;
    private static final String BORDER_COLOR = "BFBFBF";
    private static final int BORDER_SIZE_EIGHTHS_PT = 4; // 0.5 pt
    // Holgura de la celda respecto a la foto (0.5 pt por lado): Word dibuja
    // el borde dentro de la celda y, sin ella, recortaría el filo de la foto.
    private static final int CELL_PADDING_TWIPS = 20; // 1 pt

    @Override
    public OutputFormat getSupportedFormat() {
        return OutputFormat.DOCX;
    }

    @Override
    public byte[] compose(BufferedImage photo, int copies, PhotoSheetSpec spec) {
        SheetGrid grid = spec.bestGrid();
        if (copies < 1 || copies > grid.capacity()) {
            throw new IllegalArgumentException("copies debe estar entre 1 y " + grid.capacity());
        }
        int columns = Math.min(copies, grid.columns());
        int rows = (copies + grid.columns() - 1) / grid.columns();
        // Hacia arriba: la celda nunca queda más angosta/baja que la foto.
        int cellWidthTwips = (int) Math.ceil(spec.photoWidthPt() * TWIPS_PER_POINT) + CELL_PADDING_TWIPS;
        int cellHeightTwips = (int) Math.ceil(spec.photoHeightPt() * TWIPS_PER_POINT) + CELL_PADDING_TWIPS;
        byte[] png = toPng(photo);

        try (XWPFDocument doc = new XWPFDocument()) {
            configurePage(doc, grid, spec);

            XWPFTable table = doc.createTable(rows, columns);
            configureTable(table, columns, cellWidthTwips);

            int placed = 0;
            for (int r = 0; r < rows; r++) {
                XWPFTableRow row = table.getRow(r);
                row.setHeight(cellHeightTwips); // "al menos": si Word la agranda un poco, la foto no se recorta
                for (int c = 0; c < columns; c++) {
                    XWPFTableCell cell = row.getCell(c);
                    boolean hasPhoto = placed < copies;
                    configureCell(cell, cellWidthTwips, hasPhoto);
                    if (hasPhoto) {
                        addPhoto(cell, png, spec, placed);
                        placed++;
                    }
                }
            }

            // Word exige un párrafo después de la tabla; mínimo para no abrir otra página.
            XWPFParagraph tail = doc.createParagraph();
            shrinkParagraph(tail);
            tail.createRun().setFontSize(1);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.write(out);
            return out.toByteArray();
        } catch (InvalidFormatException e) {
            throw new IllegalStateException("Error generando la hoja de fotos en Word: formato de imagen inválido", e);
        } catch (IOException e) {
            throw new UncheckedIOException("Error generando la hoja de fotos en Word", e);
        }
    }

    private void configurePage(XWPFDocument doc, SheetGrid grid, PhotoSheetSpec spec) {
        CTSectPr sectPr = doc.getDocument().getBody().isSetSectPr()
                ? doc.getDocument().getBody().getSectPr()
                : doc.getDocument().getBody().addNewSectPr();

        CTPageSz pageSize = sectPr.isSetPgSz() ? sectPr.getPgSz() : sectPr.addNewPgSz();
        pageSize.setW(BigInteger.valueOf(twips(grid.pageWidthPt())));
        pageSize.setH(BigInteger.valueOf(twips(grid.pageHeightPt())));
        pageSize.setOrient(grid.orientation() == SheetOrientation.LANDSCAPE
                ? STPageOrientation.LANDSCAPE : STPageOrientation.PORTRAIT);

        BigInteger margin = BigInteger.valueOf(twips(spec.marginPt()));
        CTPageMar pageMargin = sectPr.isSetPgMar() ? sectPr.getPgMar() : sectPr.addNewPgMar();
        pageMargin.setTop(margin);
        pageMargin.setBottom(margin);
        pageMargin.setLeft(margin);
        pageMargin.setRight(margin);
        pageMargin.setHeader(BigInteger.ZERO);
        pageMargin.setFooter(BigInteger.ZERO);
        pageMargin.setGutter(BigInteger.ZERO);
    }

    private void configureTable(XWPFTable table, int columns, int cellWidthTwips) {
        table.setWidth(columns * cellWidthTwips);
        table.setTableAlignment(TableRowAlign.CENTER);
        // Sin bordes de tabla: cada celda con foto dibuja los suyos, así las
        // celdas vacías de la última fila no muestran marcos.
        table.setTopBorder(XWPFBorderType.NONE, 0, 0, "auto");
        table.setBottomBorder(XWPFBorderType.NONE, 0, 0, "auto");
        table.setLeftBorder(XWPFBorderType.NONE, 0, 0, "auto");
        table.setRightBorder(XWPFBorderType.NONE, 0, 0, "auto");
        table.setInsideHBorder(XWPFBorderType.NONE, 0, 0, "auto");
        table.setInsideVBorder(XWPFBorderType.NONE, 0, 0, "auto");

        CTTblPr tblPr = table.getCTTbl().getTblPr();
        tblPr.addNewTblLayout().setType(STTblLayoutType.FIXED);

        // Margen interno en cero y explícito: sin él Word usa 0.08 in por
        // lado y la foto ya no cabe en la celda.
        CTTblCellMar cellMargins = tblPr.isSetTblCellMar() ? tblPr.getTblCellMar() : tblPr.addNewTblCellMar();
        for (CTTblWidth side : new CTTblWidth[] {
                cellMargins.addNewTop(), cellMargins.addNewLeft(), cellMargins.addNewBottom(), cellMargins.addNewRight()}) {
            side.setW(BigInteger.ZERO);
            side.setType(STTblWidth.DXA);
        }

        // La rejilla de columnas es obligatoria en el esquema: se reutiliza la
        // que crea POI y se reemplazan sus columnas por las del tamaño real.
        CTTblGrid tblGrid = table.getCTTbl().getTblGrid() != null
                ? table.getCTTbl().getTblGrid()
                : table.getCTTbl().addNewTblGrid();
        while (tblGrid.sizeOfGridColArray() > 0) {
            tblGrid.removeGridCol(0);
        }
        for (int c = 0; c < columns; c++) {
            tblGrid.addNewGridCol().setW(BigInteger.valueOf(cellWidthTwips));
        }
    }

    private void configureCell(XWPFTableCell cell, int cellWidthTwips, boolean withBorder) {
        CTTcPr tcPr = cell.getCTTc().isSetTcPr() ? cell.getCTTc().getTcPr() : cell.getCTTc().addNewTcPr();
        CTTblWidth width = tcPr.isSetTcW() ? tcPr.getTcW() : tcPr.addNewTcW();
        width.setW(BigInteger.valueOf(cellWidthTwips));
        width.setType(STTblWidth.DXA);

        if (withBorder) {
            CTTcBorders borders = tcPr.addNewTcBorders();
            styleBorder(borders.addNewTop());
            styleBorder(borders.addNewLeft());
            styleBorder(borders.addNewBottom());
            styleBorder(borders.addNewRight());
        }
    }

    private void styleBorder(CTBorder border) {
        border.setVal(STBorder.SINGLE);
        border.setSz(BigInteger.valueOf(BORDER_SIZE_EIGHTHS_PT));
        border.setSpace(BigInteger.ZERO);
        border.setColor(BORDER_COLOR);
    }

    private void addPhoto(XWPFTableCell cell, byte[] png, PhotoSheetSpec spec, int index) throws InvalidFormatException, IOException {
        XWPFParagraph paragraph = cell.getParagraphArray(0);
        paragraph.setAlignment(ParagraphAlignment.CENTER);
        shrinkParagraph(paragraph);

        // Fuente de 1 pt en la corrida de la imagen: el alto de la línea es el de la foto y no suma el de un texto.
        XWPFRun run = paragraph.createRun();
        run.setFontSize(1);
        try (ByteArrayInputStream imageStream = new ByteArrayInputStream(png)) {
            run.addPicture(
                    imageStream,
                    Document.PICTURE_TYPE_PNG,
                    "foto-" + index + ".png",
                    (int) Math.round(spec.photoWidthPt() * EMU_PER_POINT),
                    (int) Math.round(spec.photoHeightPt() * EMU_PER_POINT));
        }
    }

    // Sin espacio antes/después y con la marca de párrafo en 1 pt, para que
    // la altura de la fila la fije la foto y no el texto (11 pt) del párrafo.
    private void shrinkParagraph(XWPFParagraph paragraph) {
        CTPPr pPr = paragraph.getCTP().isSetPPr() ? paragraph.getCTP().getPPr() : paragraph.getCTP().addNewPPr();
        CTSpacing spacing = pPr.isSetSpacing() ? pPr.getSpacing() : pPr.addNewSpacing();
        spacing.setBefore(BigInteger.ZERO);
        spacing.setAfter(BigInteger.ZERO);
        CTParaRPr markProperties = pPr.isSetRPr() ? pPr.getRPr() : pPr.addNewRPr();
        markProperties.addNewSz().setVal(BigInteger.valueOf(2)); // medios puntos: 1 pt
    }

    private static long twips(double points) {
        return Math.round(points * TWIPS_PER_POINT);
    }

    private byte[] toPng(BufferedImage image) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Error codificando imagen a PNG", e);
        }
    }
}
