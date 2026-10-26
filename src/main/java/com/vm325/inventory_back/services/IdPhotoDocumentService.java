package com.vm325.inventory_back.services;

import com.vm325.inventory_back.imaging.CropRegion;
import com.vm325.inventory_back.imaging.DocumentType;
import com.vm325.inventory_back.imaging.EnhancementSettings;
import com.vm325.inventory_back.imaging.OutputFormat;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Único componente que conoce la regla de negocio del Proceso 3 (BPM):
 * "una foto tamaño cédula o pasaporte se recorta al tamaño estándar y se
 * repite varias veces en una hoja 4x6 lista para imprimir". Delega el
 * recorte y la composición genéricos a {@code com.vm325.inventory_back.imaging}.
 */
public interface IdPhotoDocumentService {

    /**
     * @param type       {@link DocumentType#CEDULA} o {@link DocumentType#PASAPORTE}.
     * @param copies     cantidad de copias en la hoja, o {@code null} para llenarla
     *                   (la capacidad de la hoja de ese tipo).
     * @param clientName nombre del cliente, opcional, usado solo para el mensaje
     *                   de la notificación (no se persiste nada del documento).
     * @param rotation   giro manual (0/90/180/270) aplicado antes de recortar.
     * @param region     recorte elegido por el usuario, en fracciones de la foto ya
     *                   derecha y girada, o {@code null} para recortar centrado.
     * @return el documento generado, listo para enviarse como descarga.
     */
    byte[] generate(DocumentType type, MultipartFile image, Integer copies, OutputFormat format, String clientName,
                     EnhancementSettings settings, int rotation, CropRegion region);

    /**
     * Procesa la foto (recorte + mejora) y la devuelve como PNG al tamaño de
     * una sola copia, para mostrarla antes de generar la hoja. Usa exactamente
     * el mismo procesamiento que {@link #generate}.
     */
    byte[] previewImage(DocumentType type, MultipartFile image, EnhancementSettings settings, int rotation,
                         CropRegion region);

    /** Medidas y capacidad de cada hoja de fotos registrada. */
    List<IdPhotoSpecResponse> listSpecs();
}
