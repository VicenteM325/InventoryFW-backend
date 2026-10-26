package com.vm325.inventory_back.imaging;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class PhotoSheetComposerFactory {

    private final Map<OutputFormat, PhotoSheetComposer> composersByFormat;

    public PhotoSheetComposerFactory(List<PhotoSheetComposer> composers) {
        this.composersByFormat = new EnumMap<>(OutputFormat.class);
        for (PhotoSheetComposer composer : composers) {
            composersByFormat.put(composer.getSupportedFormat(), composer);
        }
    }

    public PhotoSheetComposer get(OutputFormat format) {
        PhotoSheetComposer composer = composersByFormat.get(format);
        if (composer == null) {
            throw new IllegalArgumentException("Formato de hoja de fotos no soportado: " + format);
        }
        return composer;
    }
}
