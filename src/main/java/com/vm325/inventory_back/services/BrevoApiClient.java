package com.vm325.inventory_back.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Envío de correo por la API HTTP de Brevo (puerto 443). Existe porque algunos hostings,
 * como Railway, bloquean los puertos SMTP salientes. Solo se usa si hay BREVO_API_KEY;
 * sin ella el sistema sigue enviando por SMTP.
 */
@Component
public class BrevoApiClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${mail.brevo.api-key:}")
    private String apiKey;

    @Value("${mail.brevo.api-url:https://api.brevo.com/v3/smtp/email}")
    private String apiUrl;

    public boolean isEnabled() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** Envía un correo HTML. Lanza IOException si Brevo no responde o rechaza el envío. */
    public void send(String fromEmail, String fromName, String to, String subject, String html) throws IOException {
        send(fromEmail, fromName, to, subject, html, null);
    }

    /** Igual que {@link #send(String, String, String, String, String)}, con dirección de respuesta opcional. */
    public void send(String fromEmail, String fromName, String to, String subject, String html, String replyTo)
            throws IOException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sender", Map.of("name", fromName, "email", fromEmail));
        payload.put("to", List.of(Map.of("email", to)));
        payload.put("subject", subject);
        payload.put("htmlContent", html);
        if (replyTo != null && !replyTo.isBlank()) {
            payload.put("replyTo", Map.of("email", replyTo));
        }
        String json = mapper.writeValueAsString(payload);

        HttpRequest request = HttpRequest.newBuilder(URI.create(apiUrl))
                .timeout(TIMEOUT)
                .header("api-key", apiKey)
                .header("accept", "application/json")
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                // El cuerpo de Brevo explica el motivo (remitente no verificado, clave inválida...).
                throw new IOException("Brevo respondió " + response.statusCode() + ": " + response.body());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Envío a Brevo interrumpido", e);
        }
    }
}
