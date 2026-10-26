package com.vm325.inventory_back.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vm325.inventory_back.dtos.ApiMessage;
import com.vm325.inventory_back.imaging.CropRegion;
import com.vm325.inventory_back.imaging.DetectedCorners;
import com.vm325.inventory_back.imaging.DocumentType;
import com.vm325.inventory_back.imaging.EnhancementSettings;
import com.vm325.inventory_back.imaging.OutputFormat;
import com.vm325.inventory_back.services.DetectCornersResult;
import com.vm325.inventory_back.services.DpiDocumentService;
import com.vm325.inventory_back.services.IdPhotoDocumentService;
import com.vm325.inventory_back.services.IdPhotoSpecResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private static final DateTimeFormatter FILENAME_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final DpiDocumentService dpiDocumentService;
    private final IdPhotoDocumentService idPhotoDocumentService;
    private final ObjectMapper objectMapper;

    // Proceso 2 (BPM): generación automática de fotocopia de DPI a partir
    // de las fotos de anverso y reverso subidas por el Encargado (RF-05).
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    @PostMapping(value = "/dpi", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> generateDpiDocument(
            @RequestParam("front") MultipartFile front,
            @RequestParam("back") MultipartFile back,
            @RequestParam(defaultValue = "PDF") String format,
            @RequestParam(required = false) String clientName,
            @RequestParam(defaultValue = "0") int brightness,
            @RequestParam(defaultValue = "0") int contrast,
            @RequestParam(defaultValue = "15") int sharpness,
            @RequestParam(defaultValue = "0") int frontRotation,
            @RequestParam(defaultValue = "0") int backRotation,
            @RequestParam(required = false) String frontCorners,
            @RequestParam(required = false) String backCorners) {

        OutputFormat outputFormat = parseFormat(format);
        EnhancementSettings settings = new EnhancementSettings(brightness, contrast, sharpness);
        byte[] document = dpiDocumentService.generate(front, back, outputFormat, clientName,
                settings, frontRotation, backRotation, parseCorners(frontCorners), parseCorners(backCorners));

        String filename = "dpi_" + LocalDateTime.now().format(FILENAME_TIMESTAMP)
                + "." + outputFormat.getFileExtension();

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(outputFormat.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(document);
    }

    // Vista previa de una sola imagen (recorte + ajuste) para que el
    // frontend la muestre antes de comprometerse a generar el documento
    // final; reutiliza el mismo servicio y por lo tanto el mismo
    // procesamiento que generateDpiDocument.
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    @PostMapping(value = "/dpi/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> previewDpiImage(
            @RequestParam("image") MultipartFile image,
            @RequestParam("side") String side,
            @RequestParam(defaultValue = "0") int brightness,
            @RequestParam(defaultValue = "0") int contrast,
            @RequestParam(defaultValue = "15") int sharpness,
            @RequestParam(defaultValue = "0") int rotation,
            @RequestParam(required = false) String corners) {

        String label = "front".equalsIgnoreCase(side) ? "anverso" : "reverso";
        EnhancementSettings settings = new EnhancementSettings(brightness, contrast, sharpness);
        byte[] png = dpiDocumentService.previewImage(image, label, settings, rotation, parseCorners(corners));

        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(png);
    }

    // Detección del borde de una sola imagen, sin recortarla todavía: el
    // frontend usa esto para mostrarle al usuario dónde se detectó la
    // tarjeta y dejarlo ajustar las esquinas a mano antes de generar el
    // documento final (red de seguridad para cuando la detección automática
    // se equivoca).
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    @PostMapping(value = "/dpi/detect-corners", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DetectCornersResult> detectCorners(
            @RequestParam("image") MultipartFile image,
            @RequestParam("side") String side,
            @RequestParam(defaultValue = "0") int rotation) {

        String label = "front".equalsIgnoreCase(side) ? "anverso" : "reverso";
        return ResponseEntity.ok(dpiDocumentService.detectCorners(image, label, rotation));
    }

    // Medidas y capacidad de cada hoja de fotos: el frontend las usa para
    // dibujar el marco de recorte y limitar las copias sin duplicar constantes.
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    @GetMapping("/id-photo/specs")
    public ResponseEntity<List<IdPhotoSpecResponse>> idPhotoSpecs() {
        return ResponseEntity.ok(idPhotoDocumentService.listSpecs());
    }

    // Proceso 3 (BPM): hoja 4x6 con varias copias de una foto tamaño cédula
    // o pasaporte, recortada con el marco que eligió el Encargado.
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    @PostMapping(value = "/id-photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> generateIdPhotoDocument(
            @RequestParam("image") MultipartFile image,
            @RequestParam("type") String type,
            @RequestParam(required = false) Integer copies,
            @RequestParam(defaultValue = "PDF") String format,
            @RequestParam(required = false) String clientName,
            @RequestParam(defaultValue = "0") int brightness,
            @RequestParam(defaultValue = "0") int contrast,
            @RequestParam(defaultValue = "15") int sharpness,
            @RequestParam(defaultValue = "0") int rotation,
            @RequestParam(required = false) Double cropX,
            @RequestParam(required = false) Double cropY,
            @RequestParam(required = false) Double cropWidth,
            @RequestParam(required = false) Double cropHeight) {

        DocumentType documentType = parseDocumentType(type);
        OutputFormat outputFormat = parseFormat(format);
        byte[] document = idPhotoDocumentService.generate(documentType, image, copies, outputFormat, clientName,
                parseSettings(brightness, contrast, sharpness), rotation,
                parseCropRegion(cropX, cropY, cropWidth, cropHeight));

        String filename = "foto_" + documentType.name().toLowerCase() + "_"
                + LocalDateTime.now().format(FILENAME_TIMESTAMP) + "." + outputFormat.getFileExtension();

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(outputFormat.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(document);
    }

    // Vista previa de una sola copia (recorte + ajuste) con el mismo
    // procesamiento que generateIdPhotoDocument.
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    @PostMapping(value = "/id-photo/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> previewIdPhoto(
            @RequestParam("image") MultipartFile image,
            @RequestParam("type") String type,
            @RequestParam(defaultValue = "0") int brightness,
            @RequestParam(defaultValue = "0") int contrast,
            @RequestParam(defaultValue = "15") int sharpness,
            @RequestParam(defaultValue = "0") int rotation,
            @RequestParam(required = false) Double cropX,
            @RequestParam(required = false) Double cropY,
            @RequestParam(required = false) Double cropWidth,
            @RequestParam(required = false) Double cropHeight) {

        byte[] png = idPhotoDocumentService.previewImage(parseDocumentType(type), image,
                parseSettings(brightness, contrast, sharpness), rotation,
                parseCropRegion(cropX, cropY, cropWidth, cropHeight));

        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(png);
    }

    // Un parámetro numérico que no se puede convertir (p.ej. cropX=abc) es un
    // error del cliente; sin esto el handler genérico lo respondería como 500.
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiMessage> invalidParameter(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest().body(new ApiMessage("Parámetro inválido: " + e.getName()));
    }

    private DocumentType parseDocumentType(String type) {
        try {
            DocumentType documentType = DocumentType.valueOf(type.trim().toUpperCase());
            if (documentType == DocumentType.DPI) {
                throw new IllegalArgumentException(type);
            }
            return documentType;
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Tipo de foto no soportado: " + type + " (use CEDULA o PASAPORTE)");
        }
    }

    private EnhancementSettings parseSettings(int brightness, int contrast, int sharpness) {
        try {
            return new EnhancementSettings(brightness, contrast, sharpness);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    // Los cuatro valores del marco o ninguno (sin marco = recorte centrado).
    private CropRegion parseCropRegion(Double x, Double y, Double width, Double height) {
        if (x == null && y == null && width == null && height == null) {
            return null;
        }
        if (x == null || y == null || width == null || height == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El recorte requiere cropX, cropY, cropWidth y cropHeight");
        }
        try {
            return new CropRegion(x, y, width, height);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    private DetectedCorners parseCorners(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, DetectedCorners.class);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Formato de esquinas inválido");
        }
    }

    private OutputFormat parseFormat(String format) {
        try {
            return OutputFormat.valueOf(format.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Formato no soportado: " + format + " (use PDF o DOCX)");
        }
    }
}
