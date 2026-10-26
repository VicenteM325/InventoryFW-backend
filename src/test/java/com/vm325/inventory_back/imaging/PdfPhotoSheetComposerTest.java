package com.vm325.inventory_back.imaging;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSNumber;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfPhotoSheetComposerTest {

    private final DocumentLayoutRegistry registry = new DocumentLayoutRegistry();
    private final PdfPhotoSheetComposer composer = new PdfPhotoSheetComposer();

    @Test
    void cedula_nueveCopias_unaPaginaDe4x6ConUnaSolaImagenDibujadaNueveVeces() throws IOException {
        PhotoSheetSpec spec = registry.getPhotoSheetSpec(DocumentType.CEDULA);

        byte[] pdf = composer.compose(photo(spec), 9, spec);

        try (PDDocument doc = Loader.loadPDF(pdf)) {
            assertEquals(1, doc.getNumberOfPages());
            PDRectangle box = doc.getPage(0).getMediaBox();
            assertEquals(288f, box.getWidth(), 0.01f);
            assertEquals(432f, box.getHeight(), 0.01f);
            assertEquals(9, countOperators(doc.getPage(0), "Do"));
            assertEquals(1, countImages(doc.getPage(0)));
        }
    }

    @Test
    void pasaporte_dosCopias_unaPaginaConDosDibujos() throws IOException {
        PhotoSheetSpec spec = registry.getPhotoSheetSpec(DocumentType.PASAPORTE);

        byte[] pdf = composer.compose(photo(spec), 2, spec);

        try (PDDocument doc = Loader.loadPDF(pdf)) {
            assertEquals(1, doc.getNumberOfPages());
            assertEquals(2, countOperators(doc.getPage(0), "Do"));
            assertEquals(1, countImages(doc.getPage(0)));
        }
    }

    @Test
    void unaCopia_dibujaUnaSolaVez() throws IOException {
        PhotoSheetSpec spec = registry.getPhotoSheetSpec(DocumentType.CEDULA);

        byte[] pdf = composer.compose(photo(spec), 1, spec);

        try (PDDocument doc = Loader.loadPDF(pdf)) {
            assertEquals(1, countOperators(doc.getPage(0), "Do"));
        }
    }

    @Test
    void pesoDelArchivo_noCrecePorCopia() {
        PhotoSheetSpec spec = registry.getPhotoSheetSpec(DocumentType.CEDULA);
        BufferedImage photo = photo(spec);

        int oneCopy = composer.compose(photo, 1, spec).length;
        int nineCopies = composer.compose(photo, 9, spec).length;

        assertTrue(nineCopies < oneCopy + 2_000, "La foto se está incrustando más de una vez: "
                + oneCopy + " vs " + nineCopies + " bytes");
        assertTrue(nineCopies < 2_000_000, "El PDF pesa demasiado: " + nineCopies + " bytes");
    }

    @Test
    void imagenDibujadaAlTamanoRealEnPuntos() throws IOException {
        PhotoSheetSpec spec = registry.getPhotoSheetSpec(DocumentType.PASAPORTE);

        byte[] pdf = composer.compose(photo(spec), 1, spec);

        // 5 cm = 141.73 pt: la foto no se escala al imprimirla.
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            List<Object> tokens = new PDFStreamParser(doc.getPage(0)).parse();
            Object[] cm = null;
            for (int i = 0; i < tokens.size(); i++) {
                if (tokens.get(i) instanceof Operator op && "cm".equals(op.getName())) {
                    cm = new Object[] {tokens.get(i - 6), tokens.get(i - 5), tokens.get(i - 4), tokens.get(i - 3)};
                }
            }
            assertTrue(cm != null, "No se encontró la matriz de transformación de la imagen");
            assertEquals(141.73, ((COSNumber) cm[0]).floatValue(), 0.01);
            assertEquals(141.73, ((COSNumber) cm[3]).floatValue(), 0.01);
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
    void fabrica_entregaElComposerPorFormato_yRechazaUnoSinRegistrar() {
        PhotoSheetComposerFactory factory = new PhotoSheetComposerFactory(List.of(composer));

        assertEquals(composer, factory.get(OutputFormat.PDF));
        assertThrows(IllegalArgumentException.class, () -> factory.get(OutputFormat.DOCX));
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

    private static int countOperators(PDPage page, String name) throws IOException {
        int count = 0;
        for (Object token : new PDFStreamParser(page).parse()) {
            if (token instanceof Operator op && name.equals(op.getName())) {
                count++;
            }
        }
        return count;
    }

    private static int countImages(PDPage page) {
        int count = 0;
        for (COSName ignored : page.getResources().getXObjectNames()) {
            count++;
        }
        return count;
    }
}
