package com.vm325.inventory_back.imaging;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Límite de resolución de trabajo (imágenes sintéticas, sin fotos reales):
 * las fotos más grandes que MAX_WORKING_DIMENSION_PX se reducen al
 * decodificarlas, y detectCorners y cropAndFit comparten ese mismo espacio
 * de píxeles.
 */
class ImageCropServiceImplTest {

    private static final CropSpec TEST_SPEC = new CropSpec(200, 126); // misma relación ~1.587 que el DPI real

    private final ImageCropServiceImpl service = new ImageCropServiceImpl(
            new ExifOrientationNormalizerImpl(), new CardDetectionServiceImpl(), new ImageEnhancementServiceImpl());

    @Test
    void detectCorners_fotoMasGrandeQueElLimite_seReduceAlLadoMayorPermitido() throws IOException {
        byte[] photo = png(redRectangleOnWhite(4000, 3000));

        BufferedImage normalized = service.detectCorners(photo, TEST_SPEC, 0).normalizedImage();

        assertEquals(ImageCropServiceImpl.MAX_WORKING_DIMENSION_PX, normalized.getWidth());
        assertEquals(1920, normalized.getHeight());
    }

    @Test
    void detectCorners_fotoDentroDelLimite_conservaSuTamano() throws IOException {
        byte[] photo = png(redRectangleOnWhite(1600, 1200));

        BufferedImage normalized = service.detectCorners(photo, TEST_SPEC, 0).normalizedImage();

        assertEquals(1600, normalized.getWidth());
        assertEquals(1200, normalized.getHeight());
    }

    @Test
    void cropAndFit_esquinasManuales_seInterpretanEnElEspacioReducidoDeDetectCorners() throws IOException {
        // En la foto de 4000x3000 el rectángulo rojo ocupa (1000,750)-(3000,2010);
        // reducida a 2560 px (escala 0.64) queda en (640,480)-(1920,1286), que
        // es donde el usuario vería y ajustaría las esquinas. Si cropAndFit las
        // aplicara sobre la foto original, el 10 % superior izquierdo del
        // recorte caería en el fondo blanco.
        byte[] photo = png(redRectangleOnWhite(4000, 3000));
        DetectedCorners corners = new DetectedCorners(
                new CornerPoint(640, 480), new CornerPoint(1920, 480),
                new CornerPoint(1920, 1286), new CornerPoint(640, 1286));

        BufferedImage result = service.cropAndFit(photo, TEST_SPEC, new EnhancementSettings(0, 0, 0), 0, corners);

        assertEquals(TEST_SPEC.targetWidthPx(), result.getWidth());
        assertEquals(TEST_SPEC.targetHeightPx(), result.getHeight());
        Color nearTopLeft = new Color(result.getRGB(result.getWidth() / 10, result.getHeight() / 10));
        assertTrue(nearTopLeft.getRed() > 200 && nearTopLeft.getGreen() < 80 && nearTopLeft.getBlue() < 80,
                "El recorte debería ser el rectángulo rojo, pero el píxel es " + nearTopLeft);
    }

    // --- cropRegionAndFit: recorte por región elegida por el usuario ---

    private static final CropSpec SQUARE_SPEC = new CropSpec(100, 100);
    private static final EnhancementSettings NO_ENHANCEMENT = new EnhancementSettings(0, 0, 0);

    @Test
    void cropRegionAndFit_tamanoDeSalidaEsElDelCropSpec() throws IOException {
        byte[] photo = png(quadrants(800, 600));
        CropSpec cedulaSpec = new CropSpec(366, 461);

        BufferedImage result = service.cropRegionAndFit(
                photo, cedulaSpec, NO_ENHANCEMENT, 0, new CropRegion(0.1, 0.1, 0.5, 0.8));

        assertEquals(366, result.getWidth());
        assertEquals(461, result.getHeight());
    }

    @Test
    void cropRegionAndFit_elContenidoDeLaRegionLlegaALaSalida() throws IOException {
        byte[] photo = png(quadrants(400, 400));

        // Cuadrante superior derecho: verde.
        BufferedImage result = service.cropRegionAndFit(
                photo, SQUARE_SPEC, NO_ENHANCEMENT, 0, new CropRegion(0.5, 0, 0.5, 0.5));

        assertColor(result, 10, 10, Color.GREEN);
        assertColor(result, 90, 90, Color.GREEN);
    }

    @Test
    void cropRegionAndFit_regionConOtraProporcion_seRecortaSinDeformar() throws IOException {
        // Franjas verticales: rojo (0-25 %), azul (25-75 %), rojo (75-100 %).
        // La región es 100 % ancho x 50 % alto, o sea 2:1; el destino es 1:1,
        // así que se recortan los lados alrededor del centro (25-75 %: todo
        // azul). Si se estirara a 1:1 se verían las franjas rojas.
        BufferedImage stripes = new BufferedImage(400, 400, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = stripes.createGraphics();
        try {
            g.setColor(Color.RED);
            g.fillRect(0, 0, 400, 400);
            g.setColor(Color.BLUE);
            g.fillRect(100, 0, 200, 400);
        } finally {
            g.dispose();
        }

        BufferedImage result = service.cropRegionAndFit(
                png(stripes), SQUARE_SPEC, NO_ENHANCEMENT, 0, new CropRegion(0, 0.25, 1, 0.5));

        assertColor(result, 2, 50, Color.BLUE);
        assertColor(result, 97, 50, Color.BLUE);
    }

    @Test
    void cropRegionAndFit_elGiroSeAplicaAntesDeLaRegion() throws IOException {
        // 400x200: mitad izquierda roja, mitad derecha azul. Girada 90° en
        // sentido horario queda 200x400 con el rojo arriba y el azul abajo; la
        // región (en el espacio ya girado) es la mitad superior.
        BufferedImage halves = new BufferedImage(400, 200, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = halves.createGraphics();
        try {
            g.setColor(Color.RED);
            g.fillRect(0, 0, 200, 200);
            g.setColor(Color.BLUE);
            g.fillRect(200, 0, 200, 200);
        } finally {
            g.dispose();
        }

        BufferedImage result = service.cropRegionAndFit(
                png(halves), SQUARE_SPEC, NO_ENHANCEMENT, 90, new CropRegion(0, 0, 1, 0.5));

        assertColor(result, 50, 50, Color.RED);
        assertColor(result, 5, 5, Color.RED);
        assertColor(result, 95, 95, Color.RED);
    }

    @Test
    void cropRegionAndFit_mismaRegionEnOtraResolucion_dalElMismoRecorte() throws IOException {
        CropRegion region = new CropRegion(0, 0, 0.5, 0.5);

        BufferedImage large = service.cropRegionAndFit(
                png(quadrants(4000, 3000)), SQUARE_SPEC, NO_ENHANCEMENT, 0, region);
        BufferedImage small = service.cropRegionAndFit(
                png(quadrants(2000, 1500)), SQUARE_SPEC, NO_ENHANCEMENT, 0, region);

        assertColor(large, 50, 50, Color.RED);
        assertColor(small, 50, 50, Color.RED);
        assertColor(large, 5, 5, Color.RED);
        assertColor(small, 95, 95, Color.RED);
    }

    @Test
    void cropRegionAndFit_sinRegion_recortaCentrado() throws IOException {
        // 400x200: izquierda roja, derecha azul. Centrado a 1:1 queda
        // 200x200 desde x=100: mitad roja y mitad azul.
        BufferedImage halves = new BufferedImage(400, 200, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = halves.createGraphics();
        try {
            g.setColor(Color.RED);
            g.fillRect(0, 0, 200, 200);
            g.setColor(Color.BLUE);
            g.fillRect(200, 0, 200, 200);
        } finally {
            g.dispose();
        }

        BufferedImage result = service.cropRegionAndFit(png(halves), SQUARE_SPEC, NO_ENHANCEMENT, 0, null);

        assertEquals(100, result.getWidth());
        assertColor(result, 5, 50, Color.RED);
        assertColor(result, 95, 50, Color.BLUE);
    }

    @Test
    void cropRegionAndFit_imagenGrande_seReduceSinPerderElPromedioDeColor() throws IOException {
        // Tablero de 1 px a gran resolución: un solo paso bilinear de 20x se
        // saltaría píxeles y daría negro o blanco puro; reduciendo en pasos
        // el resultado es el gris medio.
        BufferedImage checker = new BufferedImage(2000, 2000, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 2000; y++) {
            for (int x = 0; x < 2000; x++) {
                checker.setRGB(x, y, ((x + y) % 2 == 0) ? 0xFFFFFF : 0x000000);
            }
        }

        BufferedImage result = service.cropRegionAndFit(
                png(checker), SQUARE_SPEC, NO_ENHANCEMENT, 0, new CropRegion(0, 0, 1, 1));

        Color center = new Color(result.getRGB(50, 50));
        assertTrue(center.getRed() > 90 && center.getRed() < 165,
                "Debería ser un gris medio, pero el píxel es " + center);
    }

    @Test
    void cropRegionAndFit_imagenInvalida_lanzaError() {
        assertThrows(IllegalArgumentException.class, () ->
                service.cropRegionAndFit(new byte[] {1, 2, 3}, SQUARE_SPEC, NO_ENHANCEMENT, 0, null));
    }

    @Test
    void cropRegionAndFit_giroInvalido_lanzaError() throws IOException {
        byte[] photo = png(quadrants(200, 200));

        assertThrows(IllegalArgumentException.class, () ->
                service.cropRegionAndFit(photo, SQUARE_SPEC, NO_ENHANCEMENT, 45, null));
    }

    // Cuatro cuadrantes de color: arriba izquierda rojo, arriba derecha
    // verde, abajo izquierda azul, abajo derecha amarillo.
    private static BufferedImage quadrants(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            int halfWidth = width / 2;
            int halfHeight = height / 2;
            g.setColor(Color.RED);
            g.fillRect(0, 0, halfWidth, halfHeight);
            g.setColor(Color.GREEN);
            g.fillRect(halfWidth, 0, width - halfWidth, halfHeight);
            g.setColor(Color.BLUE);
            g.fillRect(0, halfHeight, halfWidth, height - halfHeight);
            g.setColor(Color.YELLOW);
            g.fillRect(halfWidth, halfHeight, width - halfWidth, height - halfHeight);
        } finally {
            g.dispose();
        }
        return image;
    }

    private static void assertColor(BufferedImage image, int x, int y, Color expected) {
        Color actual = new Color(image.getRGB(x, y));
        int tolerance = 40;
        assertTrue(Math.abs(actual.getRed() - expected.getRed()) <= tolerance
                        && Math.abs(actual.getGreen() - expected.getGreen()) <= tolerance
                        && Math.abs(actual.getBlue() - expected.getBlue()) <= tolerance,
                "En (" + x + "," + y + ") se esperaba " + expected + " pero hay " + actual);
    }

    private static BufferedImage redRectangleOnWhite(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);
            g.setColor(Color.RED);
            g.fillRect(width / 4, height / 4, width / 2, (int) Math.round(height * 0.42));
        } finally {
            g.dispose();
        }
        return image;
    }

    private static byte[] png(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
