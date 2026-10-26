package com.vm325.inventory_back.imaging;

/**
 * Tipo de documento que el motor de recorte/composición de imágenes puede
 * generar.
 * <p>
 * DPI (Proceso 2 del análisis BPM) es una hoja carta con imágenes apiladas
 * y se describe con un {@link DocumentLayoutSpec} en
 * {@link DocumentLayoutRegistry}. CEDULA y PASAPORTE (Proceso 3) son hojas de
 * fotos 4x6 con N copias de una misma foto en rejilla, así que se describen
 * con un {@link PhotoSheetSpec} y se componen con un
 * {@link PhotoSheetComposer}, no con un {@link DocumentComposer}.
 */
public enum DocumentType {
    DPI,
    CEDULA,
    PASAPORTE
}
