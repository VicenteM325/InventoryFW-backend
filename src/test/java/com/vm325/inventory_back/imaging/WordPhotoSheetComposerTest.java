package com.vm325.inventory_back.imaging;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.junit.jupiter.api.Test;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STPageOrientation;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Estructura del .docx (no hay un renderizador de Word en el entorno de
 * pruebas): tamaño de página, márgenes, una celda por copia, tamaño real de
 * cada foto y una sola imagen incrustada.
 */
class WordPhotoSheetComposerTest {

    private static final long EMU_PER_POINT = 12700;

    private final DocumentLayoutRegistry registry = new DocumentLayoutRegistry();
    private final WordPhotoSheetComposer composer = new WordPhotoSheetComposer();

    @Test
    void cedula_nueveCopias_paginaDe4x6ConMargenYTablaDe3x3() throws IOException {
        PhotoSheetSpec spec = registry.getPhotoSheetSpec(DocumentType.CEDULA);

        try (XWPFDocument doc = open(composer.compose(photo(spec), 9, spec))) {
            CTSectPr sectPr = doc.getDocument().getBody().getSectPr();
            CTPageSz size = sectPr.getPgSz();
            assertEquals(5760L, toLong(size.getW()));   // 4 in
            assertEquals(8640L, toLong(size.getH()));   // 6 in
            assertEquals(STPageOrientation.PORTRAIT, size.getOrient());
            CTPageMar margin = sectPr.getPgMar();
            for (Object side : List.of(margin.getTop(), margin.getBottom(), margin.getLeft(), margin.getRight())) {
                assertEquals(170L, toLong(side));       // 8.5 pt
            }

            assertEquals(1, doc.getTables().size());
            XWPFTable table = doc.getTables().get(0);
            assertEquals(3, table.getNumberOfRows());
            for (XWPFTableRow row : table.getRows()) {
                assertEquals(3, row.getTableCells().size());
            }
        }
    }

    @Test
    void unaSolaImagenIncrustada_aunqueHayaNueveCopias() throws IOException {
        PhotoSheetSpec spec = registry.getPhotoSheetSpec(DocumentType.CEDULA);

        try (XWPFDocument doc = open(composer.compose(photo(spec), 9, spec))) {
            assertEquals(1, doc.getAllPictures().size());
            assertEquals(9, countPhotos(doc.getTables().get(0)));
        }
    }

    @Test
    void cadaFotoMideSuTamanoRealYCabeEnSuCelda() throws IOException {
        PhotoSheetSpec spec = registry.getPhotoSheetSpec(DocumentType.PASAPORTE);

        try (XWPFDocument doc = open(composer.compose(photo(spec), 2, spec))) {
            XWPFTableCell cell = doc.getTables().get(0).getRow(0).getCell(0);
            String xml = cell.getCTTc().xmlText();

            // 5 cm = 141.73 pt = 1 800 000 EMU.
            long expectedEmu = Math.round(spec.photoWidthPt() * EMU_PER_POINT);
            assertTrue(xml.contains("cx=\"" + expectedEmu + "\""), "No aparece el ancho real de la foto: " + xml);
            assertTrue(xml.contains("cy=\"" + expectedEmu + "\""));
            long cellWidthTwips = toLong(cell.getCTTc().getTcPr().getTcW().getW());
            assertTrue(cellWidthTwips * 635L >= expectedEmu, "La celda es más angosta que la foto");
        }
    }

    @Test
    void ultimaFilaIncompleta_lasCeldasVaciasNoLlevanBorde() throws IOException {
        PhotoSheetSpec spec = registry.getPhotoSheetSpec(DocumentType.CEDULA);

        try (XWPFDocument doc = open(composer.compose(photo(spec), 5, spec))) {
            XWPFTable table = doc.getTables().get(0);
            assertEquals(2, table.getNumberOfRows());
            assertEquals(5, countPhotos(table));
            assertTrue(table.getRow(1).getCell(1).getCTTc().getTcPr().isSetTcBorders());
            assertTrue(!table.getRow(1).getCell(2).getCTTc().getTcPr().isSetTcBorders(),
                    "La celda vacía no debería dibujar un marco");
        }
    }

    @Test
    void menosCopiasQueColumnas_laTablaSoloTieneLasColumnasNecesarias() throws IOException {
        PhotoSheetSpec spec = registry.getPhotoSheetSpec(DocumentType.CEDULA);

        try (XWPFDocument doc = open(composer.compose(photo(spec), 2, spec))) {
            XWPFTable table = doc.getTables().get(0);
            assertEquals(1, table.getNumberOfRows());
            assertEquals(2, table.getRow(0).getTableCells().size());
        }
    }

    @Test
    void anchoDeLaTabla_cabeEnElAreaUtilDeLaPagina() throws IOException {
        for (DocumentType type : List.of(DocumentType.CEDULA, DocumentType.PASAPORTE)) {
            PhotoSheetSpec spec = registry.getPhotoSheetSpec(type);

            try (XWPFDocument doc = open(composer.compose(photo(spec), spec.capacity(), spec))) {
                CTSectPr sectPr = doc.getDocument().getBody().getSectPr();
                long usableWidth = toLong(sectPr.getPgSz().getW()) - 2 * toLong(sectPr.getPgMar().getLeft());
                long usableHeight = toLong(sectPr.getPgSz().getH()) - 2 * toLong(sectPr.getPgMar().getTop());
                XWPFTable table = doc.getTables().get(0);
                long tableWidth = table.getCTTbl().getTblGrid().getGridColList().stream()
                        .mapToLong(col -> toLong(col.getW())).sum();
                long tableHeight = table.getRows().stream()
                        .mapToLong(row -> row.getHeight()).sum();

                assertTrue(tableWidth <= usableWidth, type + ": la tabla (" + tableWidth + ") excede el ancho útil (" + usableWidth + ")");
                assertTrue(tableHeight <= usableHeight, type + ": la tabla (" + tableHeight + ") excede el alto útil (" + usableHeight + ")");
            }
        }
    }

    @Test
    void layoutFijoYSinBordesDeTabla() throws IOException {
        PhotoSheetSpec spec = registry.getPhotoSheetSpec(DocumentType.PASAPORTE);

        try (XWPFDocument doc = open(composer.compose(photo(spec), 2, spec))) {
            XWPFTable table = doc.getTables().get(0);
            assertNotNull(table.getCTTbl().getTblPr().getTblLayout());
            assertEquals("fixed", table.getCTTbl().getTblPr().getTblLayout().getType().toString());
            assertEquals("none", table.getCTTbl().getTblPr().getTblBorders().getTop().getVal().toString());
        }
    }

    @Test
    void margenInternoDeLasCeldas_esCeroYExplicito() throws IOException {
        PhotoSheetSpec spec = registry.getPhotoSheetSpec(DocumentType.CEDULA);

        try (XWPFDocument doc = open(composer.compose(photo(spec), 9, spec))) {
            var margins = doc.getTables().get(0).getCTTbl().getTblPr().getTblCellMar();
            // Sin valores explícitos Word aplica 0.08 in por lado y la foto no cabe en la celda.
            for (Object side : List.of(margins.getTop().getW(), margins.getLeft().getW(),
                    margins.getBottom().getW(), margins.getRight().getW())) {
                assertEquals(0L, toLong(side));
            }
        }
    }

    @Test
    void celdaMasGrandeQueLaFoto_dejaHolguraParaElBorde() throws IOException {
        PhotoSheetSpec spec = registry.getPhotoSheetSpec(DocumentType.CEDULA);

        try (XWPFDocument doc = open(composer.compose(photo(spec), 9, spec))) {
            XWPFTable table = doc.getTables().get(0);
            double photoWidthTwips = spec.photoWidthPt() * 20;
            double photoHeightTwips = spec.photoHeightPt() * 20;
            long cellWidth = toLong(table.getRow(0).getCell(0).getCTTc().getTcPr().getTcW().getW());

            assertTrue(cellWidth >= photoWidthTwips + 20, "Sin holgura para el borde: " + cellWidth);
            assertTrue(table.getRow(0).getHeight() >= photoHeightTwips + 20);
        }
    }

    @Test
    void copiasFueraDeRango_lanzaError() {
        PhotoSheetSpec spec = registry.getPhotoSheetSpec(DocumentType.PASAPORTE);
        BufferedImage photo = photo(spec);

        assertThrows(IllegalArgumentException.class, () -> composer.compose(photo, 3, spec));
        assertThrows(IllegalArgumentException.class, () -> composer.compose(photo, 0, spec));
    }

    @Test
    void fabrica_entregaElComposerWord() {
        PhotoSheetComposerFactory factory = new PhotoSheetComposerFactory(
                List.of(new PdfPhotoSheetComposer(), composer));

        assertEquals(composer, factory.get(OutputFormat.DOCX));
    }

    // Los anchos de Word son un tipo unión: POI los entrega como BigInteger o como texto ("5760").
    private static long toLong(Object twips) {
        return twips instanceof BigInteger value ? value.longValue() : Long.parseLong(twips.toString());
    }

    private static int countPhotos(XWPFTable table) {
        int count = 0;
        for (XWPFTableRow row : table.getRows()) {
            for (XWPFTableCell cell : row.getTableCells()) {
                if (cell.getCTTc().xmlText().contains("<pic:pic")) {
                    count++;
                }
            }
        }
        return count;
    }

    private static XWPFDocument open(byte[] docx) throws IOException {
        return new XWPFDocument(new ByteArrayInputStream(docx));
    }

    private static BufferedImage photo(PhotoSheetSpec spec) {
        CropSpec crop = spec.cropSpec();
        BufferedImage image = new BufferedImage(crop.targetWidthPx(), crop.targetHeightPx(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, image.getWidth(), image.getHeight());
            g.setColor(Color.DARK_GRAY);
            g.fillOval(image.getWidth() / 4, image.getHeight() / 8, image.getWidth() / 2, image.getHeight() / 2);
        } finally {
            g.dispose();
        }
        return image;
    }
}
