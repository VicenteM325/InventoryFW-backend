package com.vm325.inventory_back.imaging;

import com.vm325.inventory_back.imaging.PhotoSheetSpec.PhotoSlot;
import com.vm325.inventory_back.imaging.PhotoSheetSpec.SheetGrid;
import com.vm325.inventory_back.imaging.PhotoSheetSpec.SheetOrientation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Capacidad y distribución de las hojas de fotos 4x6 in registradas en
 * {@link DocumentLayoutRegistry}: cédula 3.1 x 3.9 cm y pasaporte 5 x 5 cm.
 */
class PhotoSheetSpecTest {

    private static final double EPS = 1e-6;

    private final DocumentLayoutRegistry registry = new DocumentLayoutRegistry();
    private final PhotoSheetSpec cedula = registry.getPhotoSheetSpec(DocumentType.CEDULA);
    private final PhotoSheetSpec pasaporte = registry.getPhotoSheetSpec(DocumentType.PASAPORTE);

    @Test
    void cedula_caben9EnVertical_y8EnHorizontal() {
        SheetGrid portrait = cedula.gridFor(SheetOrientation.PORTRAIT);
        SheetGrid landscape = cedula.gridFor(SheetOrientation.LANDSCAPE);

        assertEquals(3, portrait.columns());
        assertEquals(3, portrait.rows());
        assertEquals(4, landscape.columns());
        assertEquals(2, landscape.rows());
        assertEquals(SheetOrientation.PORTRAIT, cedula.bestGrid().orientation());
        assertEquals(9, cedula.capacity());
    }

    @Test
    void pasaporte_caben2_yEnEmpateGanaVertical() {
        SheetGrid portrait = pasaporte.gridFor(SheetOrientation.PORTRAIT);
        SheetGrid landscape = pasaporte.gridFor(SheetOrientation.LANDSCAPE);

        assertEquals(1, portrait.columns());
        assertEquals(2, portrait.rows());
        assertEquals(2, landscape.columns());
        assertEquals(1, landscape.rows());
        assertEquals(SheetOrientation.PORTRAIT, pasaporte.bestGrid().orientation());
        assertEquals(2, pasaporte.capacity());
    }

    @Test
    void cropSpec_es300dpiDelTamanoReal() {
        assertEquals(new CropSpec(366, 461), cedula.cropSpec());
        assertEquals(new CropSpec(591, 591), pasaporte.cropSpec());
    }

    @Test
    void hojaEs4x6Pulgadas() {
        SheetGrid grid = cedula.bestGrid();

        assertEquals(288d, grid.pageWidthPt(), EPS);
        assertEquals(432d, grid.pageHeightPt(), EPS);
    }

    @Test
    void layout_todasLasCantidades_dentroDelMargenYSinSolaparse() {
        for (PhotoSheetSpec spec : List.of(cedula, pasaporte)) {
            SheetGrid grid = spec.bestGrid();
            for (int copies = 1; copies <= spec.capacity(); copies++) {
                List<PhotoSlot> slots = spec.layout(copies);

                assertEquals(copies, slots.size());
                for (PhotoSlot slot : slots) {
                    assertTrue(slot.xPt() >= spec.marginPt() - EPS, "x fuera del margen: " + slot);
                    assertTrue(slot.yPt() >= spec.marginPt() - EPS, "y fuera del margen: " + slot);
                    assertTrue(slot.xPt() + slot.widthPt() <= grid.pageWidthPt() - spec.marginPt() + EPS,
                            "borde derecho fuera del margen: " + slot);
                    assertTrue(slot.yPt() + slot.heightPt() <= grid.pageHeightPt() - spec.marginPt() + EPS,
                            "borde superior fuera del margen: " + slot);
                }
                assertNoOverlapAndMinimumGap(slots, spec.gapPt());
            }
        }
    }

    @Test
    void layout_unaCopia_quedaCentradaEnLaHoja() {
        SheetGrid grid = cedula.bestGrid();

        PhotoSlot slot = cedula.layout(1).get(0);

        assertEquals((grid.pageWidthPt() - slot.widthPt()) / 2d, slot.xPt(), EPS);
        assertEquals((grid.pageHeightPt() - slot.heightPt()) / 2d, slot.yPt(), EPS);
    }

    @Test
    void layout_hojaLlena_esLaRejillaCompleta() {
        List<PhotoSlot> slots = cedula.layout(9);

        // 3 filas de 3: la primera copia es la esquina superior izquierda.
        PhotoSlot first = slots.get(0);
        PhotoSlot last = slots.get(8);
        assertTrue(first.xPt() < last.xPt());
        assertTrue(first.yPt() > last.yPt());
        assertEquals(slots.get(1).xPt() - first.xPt(), first.widthPt() + cedula.gapPt(), EPS);
    }

    @Test
    void layout_cantidadFueraDeRango_lanzaError() {
        assertThrows(IllegalArgumentException.class, () -> cedula.layout(0));
        assertThrows(IllegalArgumentException.class, () -> cedula.layout(10));
        assertThrows(IllegalArgumentException.class, () -> pasaporte.layout(3));
    }

    @Test
    void fotoMasGrandeQueLaHoja_noSePuedeCrear() {
        assertThrows(IllegalArgumentException.class, () -> new PhotoSheetSpec(
                DocumentType.PASAPORTE, 288d, 432d, 8.5d, 2d, 12d, 12d, 300));
    }

    @Test
    void medidasInvalidas_noSePuedenCrear() {
        assertThrows(IllegalArgumentException.class, () -> new PhotoSheetSpec(
                DocumentType.CEDULA, 288d, 432d, -1d, 2d, 3.1d, 3.9d, 300));
        assertThrows(IllegalArgumentException.class, () -> new PhotoSheetSpec(
                DocumentType.CEDULA, 288d, 432d, 8.5d, 2d, 3.1d, 3.9d, 0));
    }

    @Test
    void margenMayorADiezPuntos_bajaLaCapacidadDeCedula() {
        // Documenta la holgura de la cédula: 3 columnas necesitan 267.6 pt de
        // los 271 pt útiles. Con 12 pt de margen ya no caben y la hoja pasa
        // a horizontal con 8 copias.
        PhotoSheetSpec wideMargin = new PhotoSheetSpec(
                DocumentType.CEDULA, 288d, 432d, 12d, 2d, 3.1d, 3.9d, 300);

        assertEquals(8, wideMargin.capacity());
        assertEquals(SheetOrientation.LANDSCAPE, wideMargin.bestGrid().orientation());
    }

    private static void assertNoOverlapAndMinimumGap(List<PhotoSlot> slots, double gapPt) {
        for (int i = 0; i < slots.size(); i++) {
            for (int j = i + 1; j < slots.size(); j++) {
                PhotoSlot a = slots.get(i);
                PhotoSlot b = slots.get(j);
                boolean separatedHorizontally = a.xPt() + a.widthPt() + gapPt <= b.xPt() + EPS
                        || b.xPt() + b.widthPt() + gapPt <= a.xPt() + EPS;
                boolean separatedVertically = a.yPt() + a.heightPt() + gapPt <= b.yPt() + EPS
                        || b.yPt() + b.heightPt() + gapPt <= a.yPt() + EPS;
                assertTrue(separatedHorizontally || separatedVertically,
                        "Las copias " + i + " y " + j + " se solapan o están a menos de " + gapPt + " pt");
            }
        }
    }
}
