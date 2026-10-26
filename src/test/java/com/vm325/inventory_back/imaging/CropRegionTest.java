package com.vm325.inventory_back.imaging;

import org.junit.jupiter.api.Test;

import java.awt.Rectangle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CropRegionTest {

    @Test
    void regionValida_seCrea() {
        CropRegion region = new CropRegion(0.1, 0.2, 0.5, 0.6);

        assertEquals(0.5, region.width());
    }

    @Test
    void regionFueraDeLaImagen_lanzaError() {
        assertThrows(IllegalArgumentException.class, () -> new CropRegion(-0.1, 0, 0.5, 0.5));
        assertThrows(IllegalArgumentException.class, () -> new CropRegion(0, -0.1, 0.5, 0.5));
        assertThrows(IllegalArgumentException.class, () -> new CropRegion(0.6, 0, 0.5, 0.5));
        assertThrows(IllegalArgumentException.class, () -> new CropRegion(0, 0.6, 0.5, 0.5));
    }

    @Test
    void regionDemasiadoChicaONoNumerica_lanzaError() {
        assertThrows(IllegalArgumentException.class, () -> new CropRegion(0, 0, 0.005, 0.5));
        assertThrows(IllegalArgumentException.class, () -> new CropRegion(0, 0, 0.5, 0.005));
        assertThrows(IllegalArgumentException.class, () -> new CropRegion(Double.NaN, 0, 0.5, 0.5));
        assertThrows(IllegalArgumentException.class, () -> new CropRegion(0, 0, Double.POSITIVE_INFINITY, 0.5));
    }

    @Test
    void redondeoDelNavegador_unaMillonesimaFueraNoRompe() {
        // x + width = 1.000001: el frontend redondea a 6 decimales.
        CropRegion region = new CropRegion(0.123457, 0, 0.876544, 1);

        Rectangle rect = region.toPixelRect(1000, 1000, 0.876544);

        assertTrue(rect.x >= 0 && rect.x + rect.width <= 1000, "El recorte se sale de la imagen: " + rect);
        assertTrue(rect.y >= 0 && rect.y + rect.height <= 1000, "El recorte se sale de la imagen: " + rect);
    }

    @Test
    void toPixelRect_regionMasAnchaQueLaProporcion_recortaLosLadosAlrededorDelCentro() {
        CropRegion full = new CropRegion(0, 0, 1, 1);

        Rectangle rect = full.toPixelRect(1000, 500, 1.0);

        assertEquals(new Rectangle(250, 0, 500, 500), rect);
    }

    @Test
    void toPixelRect_regionMasAltaQueLaProporcion_recortaArribaYAbajoAlrededorDelCentro() {
        CropRegion full = new CropRegion(0, 0, 1, 1);

        Rectangle rect = full.toPixelRect(500, 1000, 1.0);

        assertEquals(new Rectangle(0, 250, 500, 500), rect);
    }

    @Test
    void toPixelRect_regionYaConLaProporcion_noSeModifica() {
        CropRegion region = new CropRegion(0.25, 0.25, 0.5, 0.5);

        Rectangle rect = region.toPixelRect(400, 400, 1.0);

        assertEquals(new Rectangle(100, 100, 200, 200), rect);
    }

    @Test
    void toPixelRect_mismaRegionEnOtraResolucion_esElMismoRecorteProporcional() {
        CropRegion region = new CropRegion(0.1, 0.2, 0.4, 0.3);

        Rectangle small = region.toPixelRect(2000, 1500, 4d / 3d);
        Rectangle large = region.toPixelRect(4000, 3000, 4d / 3d);

        assertEquals(small.x * 2, large.x, 2);
        assertEquals(small.y * 2, large.y, 2);
        assertEquals(small.width * 2, large.width, 2);
        assertEquals(small.height * 2, large.height, 2);
    }
}
