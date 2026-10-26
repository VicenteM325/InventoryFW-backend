package com.vm325.inventory_back.services;

import com.vm325.inventory_back.enums.NotificationType;
import com.vm325.inventory_back.imaging.CropRegion;
import com.vm325.inventory_back.imaging.DocumentLayoutRegistry;
import com.vm325.inventory_back.imaging.DocumentType;
import com.vm325.inventory_back.imaging.EnhancementSettings;
import com.vm325.inventory_back.imaging.ExifOrientationNormalizerImpl;
import com.vm325.inventory_back.imaging.ImageCropServiceImpl;
import com.vm325.inventory_back.imaging.CardDetectionServiceImpl;
import com.vm325.inventory_back.imaging.ImageEnhancementServiceImpl;
import com.vm325.inventory_back.imaging.OutputFormat;
import com.vm325.inventory_back.imaging.PdfPhotoSheetComposer;
import com.vm325.inventory_back.imaging.PhotoSheetComposerFactory;
import com.vm325.inventory_back.imaging.WordPhotoSheetComposer;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Reglas de negocio del Proceso 3 con el motor de imágenes real (fotos
 * sintéticas, sin Spring) y un {@link NotificationService} simulado.
 */
class IdPhotoDocumentServiceImplTest {

    private static final EnhancementSettings SETTINGS = EnhancementSettings.DEFAULT;

    private NotificationService notificationService;
    private IdPhotoDocumentServiceImpl service;

    @BeforeEach
    void setUp() {
        notificationService = mock(NotificationService.class);
        service = new IdPhotoDocumentServiceImpl(
                new ImageCropServiceImpl(new ExifOrientationNormalizerImpl(), new CardDetectionServiceImpl(),
                        new ImageEnhancementServiceImpl()),
                new PhotoSheetComposerFactory(List.of(new PdfPhotoSheetComposer(), new WordPhotoSheetComposer())),
                new DocumentLayoutRegistry(),
                notificationService);
    }

    @Test
    void cedula_sinCopias_llenaLaHojaConNueveYNotificaConElNombre() throws IOException {
        byte[] pdf = service.generate(DocumentType.CEDULA, jpeg(), null, OutputFormat.PDF, "Juan Pérez",
                SETTINGS, 0, null);

        assertTrue(new String(pdf, 0, 4).startsWith("%PDF"));
        assertEquals(9, countDrawnImages(pdf));
        verify(notificationService).notify(eq(NotificationType.DOCUMENTO_GENERADO),
                eq("Fotos tamaño cédula generadas para Juan Pérez"), isNull(), isNull(), isNull());
    }

    @Test
    void formatoWord_generaUnDocxConUnaCeldaPorCopia() throws IOException {
        byte[] docx = service.generate(DocumentType.CEDULA, jpeg(), 6, OutputFormat.DOCX, null,
                SETTINGS, 0, null);

        // Un .docx es un zip: empieza con "PK".
        assertEquals("PK", new String(docx, 0, 2));
        try (org.apache.poi.xwpf.usermodel.XWPFDocument doc =
                     new org.apache.poi.xwpf.usermodel.XWPFDocument(new java.io.ByteArrayInputStream(docx))) {
            assertEquals(1, doc.getAllPictures().size());
            assertEquals(2, doc.getTables().get(0).getNumberOfRows());
        }
    }

    @Test
    void pasaporte_conDosCopias_generaLaHoja() throws IOException {
        byte[] pdf = service.generate(DocumentType.PASAPORTE, png(), 2, OutputFormat.PDF, null,
                SETTINGS, 0, new CropRegion(0.1, 0.1, 0.8, 0.8));

        assertEquals(2, countDrawnImages(pdf));
        verify(notificationService).notify(eq(NotificationType.DOCUMENTO_GENERADO),
                eq("Fotos tamaño pasaporte generadas"), isNull(), isNull(), isNull());
    }

    @Test
    void cantidadDeCopiasFueraDeRango_responde400SinNotificar() {
        assertBadRequest(() -> service.generate(DocumentType.CEDULA, jpeg(), 10, OutputFormat.PDF, null,
                SETTINGS, 0, null), "entre 1 y 9");
        assertBadRequest(() -> service.generate(DocumentType.PASAPORTE, jpeg(), 3, OutputFormat.PDF, null,
                SETTINGS, 0, null), "entre 1 y 2");
        assertBadRequest(() -> service.generate(DocumentType.CEDULA, jpeg(), 0, OutputFormat.PDF, null,
                SETTINGS, 0, null), "entre 1 y 9");

        verify(notificationService, never()).notify(any(), any(), any(), any(), any());
    }

    @Test
    void tipoDpi_noEsUnaFotoYResponde400() {
        assertBadRequest(() -> service.generate(DocumentType.DPI, jpeg(), 1, OutputFormat.PDF, null,
                SETTINGS, 0, null), "CEDULA o PASAPORTE");
    }

    @Test
    void archivoVacioONoJpegPng_responde400() {
        MockMultipartFile empty = new MockMultipartFile("image", "foto.jpg", "image/jpeg", new byte[0]);
        MockMultipartFile gif = new MockMultipartFile("image", "foto.gif", "image/gif", new byte[] {1, 2, 3});

        assertBadRequest(() -> service.generate(DocumentType.CEDULA, empty, 1, OutputFormat.PDF, null,
                SETTINGS, 0, null), "Debe adjuntar");
        assertBadRequest(() -> service.generate(DocumentType.CEDULA, gif, 1, OutputFormat.PDF, null,
                SETTINGS, 0, null), "JPEG o PNG");
        assertBadRequest(() -> service.generate(DocumentType.CEDULA, null, 1, OutputFormat.PDF, null,
                SETTINGS, 0, null), "Debe adjuntar");
    }

    @Test
    void archivoQueNoEsUnaImagen_responde400() {
        MockMultipartFile corrupt = new MockMultipartFile("image", "foto.png", "image/png", new byte[] {1, 2, 3});

        assertBadRequest(() -> service.generate(DocumentType.CEDULA, corrupt, 1, OutputFormat.PDF, null,
                SETTINGS, 0, null), "La foto no es válida");
        verify(notificationService, never()).notify(any(), any(), any(), any(), any());
    }

    @Test
    void giroInvalido_responde400() {
        assertBadRequest(() -> service.generate(DocumentType.CEDULA, jpeg(), 1, OutputFormat.PDF, null,
                SETTINGS, 45, null), "La foto no es válida");
    }

    @Test
    void nombreDeClienteLargo_seTruncaA100Caracteres() throws IOException {
        String longName = "A".repeat(300);

        service.generate(DocumentType.CEDULA, jpeg(), 1, OutputFormat.PDF, longName, SETTINGS, 0, null);

        verify(notificationService).notify(eq(NotificationType.DOCUMENTO_GENERADO),
                eq("Fotos tamaño cédula generadas para " + "A".repeat(100)), isNull(), isNull(), isNull());
    }

    @Test
    void nombreDeClienteEnBlanco_noAgregaSufijo() throws IOException {
        service.generate(DocumentType.CEDULA, jpeg(), 1, OutputFormat.PDF, "   ", SETTINGS, 0, null);

        verify(notificationService).notify(eq(NotificationType.DOCUMENTO_GENERADO),
                eq("Fotos tamaño cédula generadas"), isNull(), isNull(), isNull());
    }

    @Test
    void vistaPrevia_devuelvePngDelTamanoDeUnaCopia() throws IOException {
        BufferedImage cedula = ImageIO.read(new ByteArrayInputStream(
                service.previewImage(DocumentType.CEDULA, jpeg(), SETTINGS, 0, null)));
        BufferedImage pasaporte = ImageIO.read(new ByteArrayInputStream(
                service.previewImage(DocumentType.PASAPORTE, jpeg(), SETTINGS, 0, null)));

        assertEquals(366, cedula.getWidth());
        assertEquals(461, cedula.getHeight());
        assertEquals(591, pasaporte.getWidth());
        assertEquals(591, pasaporte.getHeight());
        verify(notificationService, never()).notify(any(), any(), any(), any(), any());
    }

    @Test
    void listSpecs_informaMedidasYCapacidadDeAmbosTipos() {
        List<IdPhotoSpecResponse> specs = service.listSpecs();

        IdPhotoSpecResponse cedula = specs.stream().filter(s -> s.type().equals("CEDULA")).findFirst().orElseThrow();
        IdPhotoSpecResponse pasaporte = specs.stream().filter(s -> s.type().equals("PASAPORTE")).findFirst().orElseThrow();
        assertEquals(2, specs.size());
        assertEquals(9, cedula.capacity());
        assertEquals(366, cedula.widthPx());
        assertEquals(461, cedula.heightPx());
        assertEquals(3.1, cedula.widthCm(), 1e-9);
        assertEquals(288d, cedula.sheetWidthPt(), 1e-9);
        assertEquals(432d, cedula.sheetHeightPt(), 1e-9);
        assertEquals("PORTRAIT", cedula.orientation());
        assertEquals(2, pasaporte.capacity());
        assertEquals(591, pasaporte.widthPx());
    }

    private static void assertBadRequest(org.junit.jupiter.api.function.Executable action, String messagePart) {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, action);
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason() != null && ex.getReason().contains(messagePart),
                "Se esperaba un mensaje con '" + messagePart + "' pero fue: " + ex.getReason());
    }

    private static int countDrawnImages(byte[] pdf) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            int count = 0;
            for (Object token : new PDFStreamParser(doc.getPage(0)).parse()) {
                if (token instanceof Operator op && "Do".equals(op.getName())) {
                    count++;
                }
            }
            return count;
        }
    }

    private static MockMultipartFile jpeg() {
        return new MockMultipartFile("image", "foto.jpg", "image/jpeg", encode(samplePhoto(), "jpg"));
    }

    private static MockMultipartFile png() {
        return new MockMultipartFile("image", "foto.png", "image/png", encode(samplePhoto(), "png"));
    }

    private static BufferedImage samplePhoto() {
        BufferedImage image = new BufferedImage(1200, 1600, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, 1200, 1600);
            g.setColor(Color.DARK_GRAY);
            g.fillOval(300, 300, 600, 800);
        } finally {
            g.dispose();
        }
        return image;
    }

    private static byte[] encode(BufferedImage image, String format) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, format, out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
