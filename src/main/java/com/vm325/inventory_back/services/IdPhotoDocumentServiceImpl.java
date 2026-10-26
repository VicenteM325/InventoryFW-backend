package com.vm325.inventory_back.services;

import com.vm325.inventory_back.enums.NotificationType;
import com.vm325.inventory_back.imaging.CropRegion;
import com.vm325.inventory_back.imaging.DocumentLayoutRegistry;
import com.vm325.inventory_back.imaging.DocumentType;
import com.vm325.inventory_back.imaging.EnhancementSettings;
import com.vm325.inventory_back.imaging.ImageCropService;
import com.vm325.inventory_back.imaging.OutputFormat;
import com.vm325.inventory_back.imaging.PhotoSheetComposerFactory;
import com.vm325.inventory_back.imaging.PhotoSheetSpec;
import com.vm325.inventory_back.imaging.PhotoSheetSpec.SheetGrid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Set;
import javax.imageio.ImageIO;

@Service
@RequiredArgsConstructor
public class IdPhotoDocumentServiceImpl implements IdPhotoDocumentService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png");
    private static final int MAX_CLIENT_NAME_LENGTH = 100;

    private final ImageCropService imageCropService;
    private final PhotoSheetComposerFactory photoSheetComposerFactory;
    private final DocumentLayoutRegistry documentLayoutRegistry;
    private final NotificationService notificationService;

    @Override
    public byte[] generate(DocumentType type, MultipartFile image, Integer copies, OutputFormat format,
                            String clientName, EnhancementSettings settings, int rotation, CropRegion region) {
        PhotoSheetSpec spec = specFor(type);
        validateImage(image);
        int effectiveCopies = resolveCopies(type, spec, copies);

        BufferedImage photo = readAndCrop(image, spec, settings, rotation, region);
        byte[] document = photoSheetComposerFactory.get(format).compose(photo, effectiveCopies, spec);

        notificationService.notify(
                NotificationType.DOCUMENTO_GENERADO,
                "Fotos tamaño " + label(type) + " generadas" + subject(clientName),
                null,
                null,
                null
        );

        return document;
    }

    @Override
    public byte[] previewImage(DocumentType type, MultipartFile image, EnhancementSettings settings, int rotation,
                                CropRegion region) {
        PhotoSheetSpec spec = specFor(type);
        validateImage(image);
        BufferedImage processed = readAndCrop(image, spec, settings, rotation, region);

        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(processed, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Error codificando la vista previa a PNG", e);
        }
    }

    @Override
    public List<IdPhotoSpecResponse> listSpecs() {
        return documentLayoutRegistry.listPhotoSheetSpecs().stream()
                .map(IdPhotoDocumentServiceImpl::toResponse)
                .toList();
    }

    private static IdPhotoSpecResponse toResponse(PhotoSheetSpec spec) {
        SheetGrid grid = spec.bestGrid();
        return new IdPhotoSpecResponse(
                spec.documentType().name(),
                spec.photoWidthCm(),
                spec.photoHeightCm(),
                spec.cropSpec().targetWidthPx(),
                spec.cropSpec().targetHeightPx(),
                spec.dpi(),
                grid.capacity(),
                grid.orientation().name(),
                grid.columns(),
                grid.rows(),
                grid.pageWidthPt(),
                grid.pageHeightPt(),
                spec.photoWidthPt(),
                spec.photoHeightPt(),
                spec.marginPt(),
                spec.gapPt());
    }

    private PhotoSheetSpec specFor(DocumentType type) {
        try {
            return documentLayoutRegistry.getPhotoSheetSpec(type);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Tipo de foto no soportado: " + type + " (use CEDULA o PASAPORTE)");
        }
    }

    private int resolveCopies(DocumentType type, PhotoSheetSpec spec, Integer copies) {
        int capacity = spec.capacity();
        if (copies == null) {
            return capacity;
        }
        if (copies < 1 || copies > capacity) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La cantidad de copias debe estar entre 1 y " + capacity
                            + " para fotos tamaño " + label(type));
        }
        return copies;
    }

    private BufferedImage readAndCrop(MultipartFile file, PhotoSheetSpec spec, EnhancementSettings settings,
                                       int rotation, CropRegion region) {
        try {
            return imageCropService.cropRegionAndFit(file.getBytes(), spec.cropSpec(), settings, rotation, region);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La foto no es válida: " + e.getMessage());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se pudo leer la foto");
        }
    }

    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debe adjuntar la foto");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La foto debe ser JPEG o PNG");
        }
    }

    private static String label(DocumentType type) {
        return type == DocumentType.CEDULA ? "cédula" : "pasaporte";
    }

    private static String subject(String clientName) {
        if (clientName == null || clientName.isBlank()) {
            return "";
        }
        String trimmed = clientName.trim();
        if (trimmed.length() > MAX_CLIENT_NAME_LENGTH) {
            trimmed = trimmed.substring(0, MAX_CLIENT_NAME_LENGTH);
        }
        return " para " + trimmed;
    }
}
