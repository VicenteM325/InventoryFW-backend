package com.vm325.inventory_back.imaging;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuración de una hoja de fotos tamaño carnet: la hoja de impresión
 * (p.ej. papel fotográfico 4x6 in), el margen de seguridad, la separación
 * entre copias y el tamaño real de cada foto. Un {@link PhotoSheetComposer}
 * solo necesita esta clase, una foto ya recortada y la cantidad de copias
 * para producir el documento; la hoja puede ir en vertical u horizontal,
 * según en cuál caben más copias.
 *
 * @param documentType  tipo de foto ({@link DocumentType#CEDULA} o {@link DocumentType#PASAPORTE}).
 * @param sheetWidthPt  ancho de la hoja en vertical, en puntos PDF (1 in = 72 pt).
 * @param sheetHeightPt alto de la hoja en vertical, en puntos PDF.
 * @param marginPt      margen de seguridad uniforme, en puntos.
 * @param gapPt         separación entre copias, en puntos.
 * @param photoWidthCm  ancho real de la foto impresa, en centímetros.
 * @param photoHeightCm alto real de la foto impresa, en centímetros.
 * @param dpi           resolución a la que se recorta la foto.
 */
public record PhotoSheetSpec(
        DocumentType documentType,
        double sheetWidthPt,
        double sheetHeightPt,
        double marginPt,
        double gapPt,
        double photoWidthCm,
        double photoHeightCm,
        int dpi
) {

    private static final double PT_PER_CM = 72d / 2.54d;
    private static final double CM_PER_INCH = 2.54d;
    // Evita perder una columna/fila por ruido de coma flotante cuando la foto
    // cabe exactamente.
    private static final double EPSILON = 1e-9;

    public enum SheetOrientation { PORTRAIT, LANDSCAPE }

    /** Distribución de las copias en una orientación de la hoja. */
    public record SheetGrid(SheetOrientation orientation, double pageWidthPt, double pageHeightPt,
                             int columns, int rows) {
        public int capacity() {
            return columns * rows;
        }
    }

    /** Posición de una copia; el origen es la esquina inferior izquierda de la página (como en PDF). */
    public record PhotoSlot(double xPt, double yPt, double widthPt, double heightPt) {}

    public PhotoSheetSpec {
        if (sheetWidthPt <= 0 || sheetHeightPt <= 0 || marginPt < 0 || gapPt < 0
                || photoWidthCm <= 0 || photoHeightCm <= 0 || dpi <= 0) {
            throw new IllegalArgumentException("PhotoSheetSpec tiene medidas inválidas");
        }
        if (bestGrid(sheetWidthPt, sheetHeightPt, marginPt, gapPt, photoWidthCm, photoHeightCm).capacity() < 1) {
            throw new IllegalArgumentException("La foto no cabe en la hoja");
        }
    }

    public double photoWidthPt() {
        return photoWidthCm * PT_PER_CM;
    }

    public double photoHeightPt() {
        return photoHeightCm * PT_PER_CM;
    }

    /** Resolución (px) a la que se recorta cada foto para imprimirse al tamaño real a {@link #dpi()}. */
    public CropSpec cropSpec() {
        return new CropSpec(
                (int) Math.round(photoWidthCm / CM_PER_INCH * dpi),
                (int) Math.round(photoHeightCm / CM_PER_INCH * dpi));
    }

    public SheetGrid gridFor(SheetOrientation orientation) {
        return grid(orientation, sheetWidthPt, sheetHeightPt, marginPt, gapPt, photoWidthCm, photoHeightCm);
    }

    /** La orientación donde caben más copias; en empate, vertical. */
    public SheetGrid bestGrid() {
        return bestGrid(sheetWidthPt, sheetHeightPt, marginPt, gapPt, photoWidthCm, photoHeightCm);
    }

    public int capacity() {
        return bestGrid().capacity();
    }

    /**
     * Posiciones de {@code copies} copias en la mejor orientación, llenando
     * fila por fila y con el bloque resultante centrado en la hoja.
     *
     * @throws IllegalArgumentException si {@code copies} no está entre 1 y {@link #capacity()}.
     */
    public List<PhotoSlot> layout(int copies) {
        SheetGrid grid = bestGrid();
        if (copies < 1 || copies > grid.capacity()) {
            throw new IllegalArgumentException(
                    "copies debe estar entre 1 y " + grid.capacity());
        }
        double photoWidth = photoWidthPt();
        double photoHeight = photoHeightPt();
        int usedColumns = Math.min(copies, grid.columns());
        int usedRows = (copies + grid.columns() - 1) / grid.columns();
        double blockWidth = usedColumns * photoWidth + (usedColumns - 1) * gapPt;
        double blockHeight = usedRows * photoHeight + (usedRows - 1) * gapPt;
        double left = (grid.pageWidthPt() - blockWidth) / 2d;
        double top = grid.pageHeightPt() - (grid.pageHeightPt() - blockHeight) / 2d;

        List<PhotoSlot> slots = new ArrayList<>(copies);
        for (int i = 0; i < copies; i++) {
            int column = i % grid.columns();
            int row = i / grid.columns();
            slots.add(new PhotoSlot(
                    left + column * (photoWidth + gapPt),
                    top - (row + 1) * photoHeight - row * gapPt,
                    photoWidth,
                    photoHeight));
        }
        return slots;
    }

    private static SheetGrid bestGrid(double sheetWidthPt, double sheetHeightPt, double marginPt, double gapPt,
                                       double photoWidthCm, double photoHeightCm) {
        SheetGrid portrait = grid(SheetOrientation.PORTRAIT, sheetWidthPt, sheetHeightPt, marginPt, gapPt,
                photoWidthCm, photoHeightCm);
        SheetGrid landscape = grid(SheetOrientation.LANDSCAPE, sheetWidthPt, sheetHeightPt, marginPt, gapPt,
                photoWidthCm, photoHeightCm);
        return landscape.capacity() > portrait.capacity() ? landscape : portrait;
    }

    private static SheetGrid grid(SheetOrientation orientation, double sheetWidthPt, double sheetHeightPt,
                                   double marginPt, double gapPt, double photoWidthCm, double photoHeightCm) {
        boolean portrait = orientation == SheetOrientation.PORTRAIT;
        double pageWidth = portrait ? sheetWidthPt : sheetHeightPt;
        double pageHeight = portrait ? sheetHeightPt : sheetWidthPt;
        double photoWidth = photoWidthCm * PT_PER_CM;
        double photoHeight = photoHeightCm * PT_PER_CM;
        int columns = fit(pageWidth - 2 * marginPt, photoWidth, gapPt);
        int rows = fit(pageHeight - 2 * marginPt, photoHeight, gapPt);
        return new SheetGrid(orientation, pageWidth, pageHeight, columns, rows);
    }

    // n fotos ocupan n * foto + (n - 1) * gap: n = floor((útil + gap) / (foto + gap)).
    private static int fit(double usable, double photo, double gap) {
        return Math.max(0, (int) Math.floor((usable + gap) / (photo + gap) + EPSILON));
    }
}
