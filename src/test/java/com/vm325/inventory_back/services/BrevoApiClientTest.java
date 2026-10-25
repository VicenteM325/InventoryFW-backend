package com.vm325.inventory_back.services;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class BrevoApiClientTest {

    private HttpServer server;
    private final AtomicReference<String> body = new AtomicReference<>();
    private final AtomicReference<String> apiKeyHeader = new AtomicReference<>();
    private volatile int status = 201;
    private volatile String reply = "{\"messageId\":\"<1@x>\"}";
    private BrevoApiClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v3/smtp/email", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            apiKeyHeader.set(exchange.getRequestHeaders().getFirst("api-key"));
            byte[] out = reply.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, out.length);
            exchange.getResponseBody().write(out);
            exchange.close();
        });
        server.start();

        client = new BrevoApiClient();
        ReflectionTestUtils.setField(client, "apiKey", "key-123");
        ReflectionTestUtils.setField(client, "apiUrl",
                "http://127.0.0.1:" + server.getAddress().getPort() + "/v3/smtp/email");
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void sendsSenderRecipientSubjectAndHtmlWithApiKeyHeader() throws IOException {
        client.send("yo@gmail.com", "Multiservicios Wilson", "user@test.com", "Asunto", "<p>Hola ñ</p>");

        assertEquals("key-123", apiKeyHeader.get());
        assertTrue(body.get().contains("\"email\":\"yo@gmail.com\""), body.get());
        assertTrue(body.get().contains("\"name\":\"Multiservicios Wilson\""));
        assertTrue(body.get().contains("\"to\":[{\"email\":\"user@test.com\"}]"));
        assertTrue(body.get().contains("\"subject\":\"Asunto\""));
        assertTrue(body.get().contains("<p>Hola ñ</p>"));
    }

    @Test
    void rejectedRequestSurfacesBrevoReason() {
        status = 400;
        reply = "{\"message\":\"Sender is not valid\"}";

        IOException ex = assertThrows(IOException.class,
                () -> client.send("yo@gmail.com", "X", "user@test.com", "A", "<p/>"));
        assertTrue(ex.getMessage().contains("400"));
        assertTrue(ex.getMessage().contains("Sender is not valid"));
    }

    @Test
    void isDisabledWithoutApiKey() {
        ReflectionTestUtils.setField(client, "apiKey", "");
        assertFalse(client.isEnabled());
    }
}
