package com.vm325.inventory_back.imaging;

import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * Corrige orientación EXIF, intenta detectar y enderezar automáticamente el
 * borde de la tarjeta con {@link CardDetectionService} (o cae al recorte
 * centrado si no encuentra un contorno confiable), reescala a la resolución
 * objetivo y aplica el ajuste de color/nitidez de {@link ImageEnhancementService}.
 */
@Service
public class ImageCropServiceImpl implements ImageCropService {

    // Las fotos de celular llegan a 12 MP o más, pero el recorte final mide
    // ~1000 px de ancho (CropSpec): procesarlas completas solo cuesta memoria
    // y tiempo (con 1 vCPU, ~13 s por documento y ~950 MiB con dos a la vez).
    // Es el mismo límite que aplica el frontend antes de subirlas; aquí
    // cubre a cualquier cliente que mande la foto original.
    static final int MAX_WORKING_DIMENSION_PX = 2560;

    private final ExifOrientationNormalizer exifOrientationNormalizer;
    private final CardDetectionService cardDetectionService;
    private final ImageEnhancementService imageEnhancementService;

    public ImageCropServiceImpl(ExifOrientationNormalizer exifOrientationNormalizer,
                                 CardDetectionService cardDetectionService,
                                 ImageEnhancementService imageEnhancementService) {
        this.exifOrientationNormalizer = exifOrientationNormalizer;
        this.cardDetectionService = cardDetectionService;
        this.imageEnhancementService = imageEnhancementService;
    }

    @Override
    public BufferedImage cropAndFit(byte[] rawImage, CropSpec spec, EnhancementSettings settings, int rotationOverride,
                                     DetectedCorners manualCorners) {
        BufferedImage source = decode(rawImage);
        BufferedImage upright = exifOrientationNormalizer.normalize(rawImage, source);
        BufferedImage rotated = ImageRotationUtil.rotateClockwise(upright, rotationOverride);
        BufferedImage cropped = (manualCorners != null
                ? cardDetectionService.rectify(rotated, manualCorners, spec)
                : cardDetectionService.detectAndRectify(rotated, spec))
                .orElseGet(() -> centerCropToAspectRatio(rotated, spec.aspectRatio()));
        BufferedImage scaled = scaleTo(cropped, spec.targetWidthPx(), spec.targetHeightPx());
        return imageEnhancementService.enhance(scaled, settings);
    }

    @Override
    public NormalizedImageWithCorners detectCorners(byte[] rawImage, CropSpec spec, int rotationOverride) {
        BufferedImage source = decode(rawImage);
        BufferedImage upright = exifOrientationNormalizer.normalize(rawImage, source);
        BufferedImage rotated = ImageRotationUtil.rotateClockwise(upright, rotationOverride);
        return new NormalizedImageWithCorners(rotated, cardDetectionService.detectCorners(rotated, spec));
    }

    private BufferedImage decode(byte[] rawImage) {
        BufferedImage source;
        try {
            source = ImageIO.read(new ByteArrayInputStream(rawImage));
        } catch (IOException e) {
            throw new IllegalArgumentException("No se pudo leer la imagen: " + e.getMessage(), e);
        }
        if (source == null) {
            throw new IllegalArgumentException("El archivo no es una imagen válida o su formato no es soportado");
        }
        return limitResolution(source);
    }

    // Se reduce justo al decodificar, antes de EXIF y rotación (que conservan
    // el lado mayor), para que detectCorners y cropAndFit trabajen siempre en
    // el mismo espacio de píxeles: las esquinas que el usuario ajusta sobre la
    // imagen de detectCorners valen tal cual al generar el documento.
    private BufferedImage limitResolution(BufferedImage source) {
        int longestSide = Math.max(source.getWidth(), source.getHeight());
        if (longestSide <= MAX_WORKING_DIMENSION_PX) {
            return source;
        }
        double scale = (double) MAX_WORKING_DIMENSION_PX / longestSide;
        return scaleTo(source,
                Math.max(1, (int) Math.round(source.getWidth() * scale)),
                Math.max(1, (int) Math.round(source.getHeight() * scale)));
    }

    private BufferedImage centerCropToAspectRatio(BufferedImage source, double targetAspectRatio) {
        int width = source.getWidth();
        int height = source.getHeight();
        double sourceAspectRatio = (double) width / (double) height;

        int cropWidth;
        int cropHeight;
        if (sourceAspectRatio > targetAspectRatio) {
            // La imagen fuente es más "ancha" que el objetivo: recortar los lados.
            cropHeight = height;
            cropWidth = (int) Math.round(height * targetAspectRatio);
        } else {
            // La imagen fuente es más "alta" que el objetivo: recortar arriba/abajo.
            cropWidth = width;
            cropHeight = (int) Math.round(width / targetAspectRatio);
        }

        int x = Math.max(0, (width - cropWidth) / 2);
        int y = Math.max(0, (height - cropHeight) / 2);
        // Asegurar que el recorte no se salga de los límites por redondeo.
        cropWidth = Math.min(cropWidth, width - x);
        cropHeight = Math.min(cropHeight, height - y);

        return source.getSubimage(x, y, cropWidth, cropHeight);
    }

    private BufferedImage scaleTo(BufferedImage source, int targetWidth, int targetHeight) {
        BufferedImage scaled = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = scaled.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, targetWidth, targetHeight);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        } finally {
            g.dispose();
        }
        return scaled;
    }
}
