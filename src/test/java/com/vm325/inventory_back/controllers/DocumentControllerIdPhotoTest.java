package com.vm325.inventory_back.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vm325.inventory_back.imaging.CropRegion;
import com.vm325.inventory_back.imaging.DocumentType;
import com.vm325.inventory_back.imaging.EnhancementSettings;
import com.vm325.inventory_back.imaging.OutputFormat;
import com.vm325.inventory_back.services.DpiDocumentService;
import com.vm325.inventory_back.services.IdPhotoDocumentService;
import com.vm325.inventory_back.services.IdPhotoSpecResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Binding de parámetros y validaciones de los endpoints /id-photo (el
 * servicio está simulado; la seguridad por rol no aplica en modo standalone).
 */
class DocumentControllerIdPhotoTest {

    private static final byte[] PDF = "%PDF-fake".getBytes();
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G'};

    private IdPhotoDocumentService idPhotoService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        idPhotoService = mock(IdPhotoDocumentService.class);
        DocumentController controller = new DocumentController(
                mock(DpiDocumentService.class), idPhotoService, new ObjectMapper());
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void generar_enviaTipoCopiasYRegion_yDevuelveElPdfComoAdjunto() throws Exception {
        when(idPhotoService.generate(any(), any(), any(), any(), any(), any(), anyInt(), any()))
                .thenReturn(PDF);

        mvc.perform(MockMvcRequestBuilders.multipart("/api/documents/id-photo")
                        .file(photo())
                        .param("type", "cedula")
                        .param("copies", "6")
                        .param("clientName", "Ana")
                        .param("rotation", "90")
                        .param("cropX", "0.1").param("cropY", "0.2")
                        .param("cropWidth", "0.5").param("cropHeight", "0.6"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition",
                        matchesPattern("attachment; filename=\"foto_cedula_\\d{8}_\\d{6}\\.pdf\"")))
                .andExpect(content().bytes(PDF));

        verify(idPhotoService).generate(eq(DocumentType.CEDULA), any(), eq(6), eq(OutputFormat.PDF), eq("Ana"),
                eq(new EnhancementSettings(0, 0, 15)), eq(90), eq(new CropRegion(0.1, 0.2, 0.5, 0.6)));
    }

    @Test
    void generar_sinMarcoNiCopias_usaRecorteCentradoYLlenaLaHoja() throws Exception {
        when(idPhotoService.generate(any(), any(), any(), any(), any(), any(), anyInt(), any()))
                .thenReturn(PDF);

        mvc.perform(MockMvcRequestBuilders.multipart("/api/documents/id-photo")
                        .file(photo())
                        .param("type", "PASAPORTE"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        containsString("foto_pasaporte_")));

        verify(idPhotoService).generate(eq(DocumentType.PASAPORTE), any(), eq(null), eq(OutputFormat.PDF), eq(null),
                any(), eq(0), eq(null));
    }

    @Test
    void generar_marcoIncompleto_responde400() throws Exception {
        mvc.perform(MockMvcRequestBuilders.multipart("/api/documents/id-photo")
                        .file(photo())
                        .param("type", "cedula")
                        .param("cropX", "0.1").param("cropY", "0.2"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(idPhotoService);
    }

    @Test
    void generar_marcoFueraDeLaImagen_responde400() throws Exception {
        mvc.perform(MockMvcRequestBuilders.multipart("/api/documents/id-photo")
                        .file(photo())
                        .param("type", "cedula")
                        .param("cropX", "0.8").param("cropY", "0")
                        .param("cropWidth", "0.5").param("cropHeight", "0.5"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(idPhotoService);
    }

    @Test
    void generar_tipoDpiODesconocido_responde400() throws Exception {
        for (String type : List.of("DPI", "licencia", "")) {
            mvc.perform(MockMvcRequestBuilders.multipart("/api/documents/id-photo")
                            .file(photo())
                            .param("type", type))
                    .andExpect(status().isBadRequest());
        }

        verifyNoInteractions(idPhotoService);
    }

    @Test
    void generar_ajusteFueraDeRango_responde400() throws Exception {
        mvc.perform(MockMvcRequestBuilders.multipart("/api/documents/id-photo")
                        .file(photo())
                        .param("type", "cedula")
                        .param("brightness", "500"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(idPhotoService);
    }

    @Test
    void generar_parametroNumericoMalFormado_responde400ConElNombreDelParametro() throws Exception {
        mvc.perform(MockMvcRequestBuilders.multipart("/api/documents/id-photo")
                        .file(photo())
                        .param("type", "cedula")
                        .param("cropX", "abc").param("cropY", "0")
                        .param("cropWidth", "0.5").param("cropHeight", "0.5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Parámetro inválido: cropX"));

        mvc.perform(MockMvcRequestBuilders.multipart("/api/documents/id-photo")
                        .file(photo())
                        .param("type", "cedula")
                        .param("copies", "muchas"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Parámetro inválido: copies"));

        verifyNoInteractions(idPhotoService);
    }

    @Test
    void generar_formatoNoSoportado_responde400() throws Exception {
        mvc.perform(MockMvcRequestBuilders.multipart("/api/documents/id-photo")
                        .file(photo())
                        .param("type", "cedula")
                        .param("format", "ODT"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void generar_sinFoto_responde400() throws Exception {
        mvc.perform(MockMvcRequestBuilders.multipart("/api/documents/id-photo")
                        .param("type", "cedula"))
                .andExpect(status().isBadRequest());

        verify(idPhotoService, never()).generate(any(), any(), any(), any(), any(), any(),
                anyInt(), any());
    }

    @Test
    void vistaPrevia_devuelvePng() throws Exception {
        when(idPhotoService.previewImage(any(), any(), any(), anyInt(), any()))
                .thenReturn(PNG);

        mvc.perform(MockMvcRequestBuilders.multipart("/api/documents/id-photo/preview")
                        .file(photo())
                        .param("type", "pasaporte")
                        .param("cropX", "0").param("cropY", "0")
                        .param("cropWidth", "1").param("cropHeight", "1"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(PNG));

        verify(idPhotoService).previewImage(eq(DocumentType.PASAPORTE), any(), any(), eq(0),
                eq(new CropRegion(0, 0, 1, 1)));
    }

    @Test
    void specs_devuelveLasHojasRegistradas() throws Exception {
        when(idPhotoService.listSpecs()).thenReturn(List.of(new IdPhotoSpecResponse(
                "CEDULA", 3.1, 3.9, 366, 461, 300, 9, "PORTRAIT", 3, 3,
                288, 432, 87.87, 110.55, 8.5, 2)));

        mvc.perform(MockMvcRequestBuilders.get("/api/documents/id-photo/specs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("CEDULA"))
                .andExpect(jsonPath("$[0].capacity").value(9))
                .andExpect(jsonPath("$[0].widthPx").value(366));
    }

    private static MockMultipartFile photo() {
        return new MockMultipartFile("image", "foto.jpg", "image/jpeg", new byte[] {1, 2, 3});
    }
}
