package com.vm325.inventory_back.imaging;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
