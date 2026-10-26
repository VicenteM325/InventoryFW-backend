package com.vm325.inventory_back.imaging;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Punto único donde se registran los {@link DocumentLayoutSpec} de los
 * documentos apilados (DPI) y los {@link PhotoSheetSpec} de las hojas de
 * fotos (cédula y pasaporte): tamaños de hoja, márgenes y medidas de foto
 * viven solo aquí.
 */
@Component
public class DocumentLayoutRegistry {

    // Tamaño carta en puntos PDF (1 in = 72 pt): 8.5in x 11in.
    private static final double LETTER_WIDTH_PT = 612d;
    private static final double LETTER_HEIGHT_PT = 792d;
    private static final double MARGIN_PT = 36d; // 0.5 in

    // DPI/cédula de identidad estándar: 85.6mm x 53.98mm, recortado a
    // ~300dpi para calidad de impresión aceptable.
    private static final CropSpec DPI_CARD_CROP = new CropSpec(1011, 638);

    // Hoja de fotos: papel fotográfico 4x6 in (288 x 432 pt), margen de
    // seguridad de ~3 mm para que cualquier impresora la imprima sin recortar
    // bordes y 2 pt entre copias. Con estas medidas caben 9 fotos de cédula
    // y 2 de pasaporte; un margen mayor a ~10 pt baja la cédula a 2 columnas.
    private static final double PHOTO_SHEET_WIDTH_PT = 288d;
    private static final double PHOTO_SHEET_HEIGHT_PT = 432d;
    private static final double PHOTO_MARGIN_PT = 8.5d;
    private static final double PHOTO_GAP_PT = 2d;
    private static final int PHOTO_DPI = 300;

    private final Map<DocumentType, DocumentLayoutSpec> specs = new EnumMap<>(DocumentType.class);
    private final Map<DocumentType, PhotoSheetSpec> photoSheetSpecs = new EnumMap<>(DocumentType.class);

    public DocumentLayoutRegistry() {
        specs.put(DocumentType.DPI, new DocumentLayoutSpec(
                DocumentType.DPI,
                LETTER_WIDTH_PT,
                LETTER_HEIGHT_PT,
                MARGIN_PT,
                DPI_CARD_CROP,
                2,
                List.of()
        ));
        photoSheetSpecs.put(DocumentType.CEDULA, photoSheet(DocumentType.CEDULA, 3.1d, 3.9d));
        photoSheetSpecs.put(DocumentType.PASAPORTE, photoSheet(DocumentType.PASAPORTE, 5d, 5d));
    }

    private static PhotoSheetSpec photoSheet(DocumentType type, double widthCm, double heightCm) {
        return new PhotoSheetSpec(type, PHOTO_SHEET_WIDTH_PT, PHOTO_SHEET_HEIGHT_PT,
                PHOTO_MARGIN_PT, PHOTO_GAP_PT, widthCm, heightCm, PHOTO_DPI);
    }

    public DocumentLayoutSpec getSpec(DocumentType type) {
        DocumentLayoutSpec spec = specs.get(type);
        if (spec == null) {
            throw new UnsupportedOperationException(
                    "No hay un DocumentLayoutSpec registrado para " + type +
                            " (probablemente un tipo de documento aún no implementado)");
        }
        return spec;
    }

    public PhotoSheetSpec getPhotoSheetSpec(DocumentType type) {
        PhotoSheetSpec spec = photoSheetSpecs.get(type);
        if (spec == null) {
            throw new IllegalArgumentException(
                    "No hay una hoja de fotos registrada para " + type);
        }
        return spec;
    }

    public List<PhotoSheetSpec> listPhotoSheetSpecs() {
        return List.copyOf(photoSheetSpecs.values());
    }
}
