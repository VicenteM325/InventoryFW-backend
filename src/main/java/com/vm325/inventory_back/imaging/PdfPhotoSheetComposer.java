package com.vm325.inventory_back.imaging;

import com.vm325.inventory_back.imaging.PhotoSheetSpec.PhotoSlot;
import com.vm325.inventory_back.imaging.PhotoSheetSpec.SheetGrid;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * Genera la hoja de fotos en PDF con el tamaño de página de la hoja de
 * impresión (p.ej. 4x6 in). La foto se incrusta una sola vez y se dibuja
 * una vez por copia, así que el archivo pesa lo mismo sin importar cuántas
 * copias lleve. Cada copia lleva un borde gris fino por fuera, como guía de
 * corte. No dibuja texto, por lo que no depende de fuentes del sistema.
 */
@Component
public class PdfPhotoSheetComposer implements PhotoSheetComposer {

    private static final float BORDER_WIDTH_PT = 0.5f;
    private static final float BORDER_GRAY = 0.75f;

    @Override
    public OutputFormat getSupportedFormat() {
        return OutputFormat.PDF;
    }

    @Override
    public byte[] compose(BufferedImage photo, int copies, PhotoSheetSpec spec) {
        SheetGrid grid = spec.bestGrid();
        List<PhotoSlot> slots = spec.layout(copies);

        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(new PDRectangle((float) grid.pageWidthPt(), (float) grid.pageHeightPt()));
            doc.addPage(page);
            PDImageXObject image = LosslessFactory.createFromImage(doc, photo);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                for (PhotoSlot slot : slots) {
                    cs.drawImage(image, (float) slot.xPt(), (float) slot.yPt(),
                            (float) slot.widthPt(), (float) slot.heightPt());
                }

                // El borde se dibuja medio trazo hacia afuera para no tapar
                // los bordes de la foto, que debe quedar a su tamaño exacto.
                float outset = BORDER_WIDTH_PT / 2f;
                cs.setStrokingColor(BORDER_GRAY);
                cs.setLineWidth(BORDER_WIDTH_PT);
                for (PhotoSlot slot : slots) {
                    cs.addRect((float) slot.xPt() - outset, (float) slot.yPt() - outset,
                            (float) slot.widthPt() + BORDER_WIDTH_PT, (float) slot.heightPt() + BORDER_WIDTH_PT);
                }
                cs.stroke();
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Error generando la hoja de fotos en PDF", e);
        }
    }
}
