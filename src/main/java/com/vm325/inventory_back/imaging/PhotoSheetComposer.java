package com.vm325.inventory_back.imaging;

import java.awt.image.BufferedImage;

/**
 * Compone una hoja de fotos: N copias de una misma foto ya recortada,
 * acomodadas en rejilla según un {@link PhotoSheetSpec}. A diferencia de
 * {@link DocumentComposer}, que apila imágenes distintas, aquí hay una sola
 * imagen y una cantidad de copias.
 */
public interface PhotoSheetComposer {

    OutputFormat getSupportedFormat();

    /**
     * @param photo  foto ya recortada y ajustada a {@link PhotoSheetSpec#cropSpec()}.
     * @param copies cantidad de copias, entre 1 y {@link PhotoSheetSpec#capacity()}.
     * @throws IllegalArgumentException si {@code copies} está fuera de rango.
     */
    byte[] compose(BufferedImage photo, int copies, PhotoSheetSpec spec);
}
