package com.vm325.inventory_back.services;

/**
 * Medidas y capacidad de una hoja de fotos (cédula o pasaporte), tal como
 * las registra {@code DocumentLayoutRegistry}. El frontend las usa para
 * dibujar el marco de recorte con la proporción correcta y limitar la
 * cantidad de copias, sin duplicar constantes. La hoja se informa ya en la
 * orientación en que caben más copias.
 */
public record IdPhotoSpecResponse(
        String type,
        double widthCm,
        double heightCm,
        int widthPx,
        int heightPx,
        int dpi,
        int capacity,
        String orientation,
        int columns,
        int rows,
        double sheetWidthPt,
        double sheetHeightPt,
        double photoWidthPt,
        double photoHeightPt,
        double marginPt,
        double gapPt
) {}
