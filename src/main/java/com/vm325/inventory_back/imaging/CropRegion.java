package com.vm325.inventory_back.imaging;

import java.awt.Rectangle;

/**
 * Región de recorte elegida por el usuario, en fracciones [0,1] de la imagen
 * ya derecha (EXIF corregido) y con el giro manual aplicado. Al ser
 * fracciones no depende de la resolución: la misma región vale para la foto
 * original y para la copia reducida que se muestra en el navegador.
 *
 * @param x      borde izquierdo, fracción del ancho.
 * @param y      borde superior, fracción del alto.
 * @param width  ancho, fracción del ancho de la imagen.
 * @param height alto, fracción del alto de la imagen.
 */
public record CropRegion(double x, double y, double width, double height) {

    private static final double MIN_SIZE = 0.01;
    // El frontend redondea a 6 decimales, por lo que x + width puede pasarse
    // de 1 por una millonésima sin que el usuario haya hecho nada raro.
    private static final double TOLERANCE = 1e-4;

    public CropRegion {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(width) || !Double.isFinite(height)) {
            throw new IllegalArgumentException("La región de recorte debe ser numérica");
        }
        if (x < 0 || y < 0 || width < MIN_SIZE || height < MIN_SIZE
                || x + width > 1 + TOLERANCE || y + height > 1 + TOLERANCE) {
            throw new IllegalArgumentException("La región de recorte queda fuera de la imagen");
        }
    }

    /**
     * Convierte la región a píxeles de una imagen de {@code imageWidth} x
     * {@code imageHeight} y la ajusta a {@code targetAspect} (ancho/alto)
     * recortando el lado más largo alrededor de su centro. Nunca estira: así
     * una diferencia de redondeo entre el navegador y el servidor no
     * deforma el rostro.
     */
    public Rectangle toPixelRect(int imageWidth, int imageHeight, double targetAspect) {
        int px = clamp((int) Math.round(x * imageWidth), 0, imageWidth - 1);
        int py = clamp((int) Math.round(y * imageHeight), 0, imageHeight - 1);
        int pw = clamp((int) Math.round(width * imageWidth), 1, imageWidth - px);
        int ph = clamp((int) Math.round(height * imageHeight), 1, imageHeight - py);

        if ((double) pw / ph > targetAspect) {
            int fittedWidth = Math.max(1, (int) Math.round(ph * targetAspect));
            px += (pw - fittedWidth) / 2;
            pw = fittedWidth;
        } else {
            int fittedHeight = Math.max(1, (int) Math.round(pw / targetAspect));
            py += (ph - fittedHeight) / 2;
            ph = fittedHeight;
        }
        return new Rectangle(px, py, pw, ph);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, Math.max(min, max)));
    }
}
