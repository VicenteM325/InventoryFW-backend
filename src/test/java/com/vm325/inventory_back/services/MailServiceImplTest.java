package com.vm325.inventory_back.services;

import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MailServiceImplTest {

    private JavaMailSender sender;
    private MailServiceImpl mailService;

    @BeforeEach
    void setUp() {
        sender = mock(JavaMailSender.class);
        when(sender.createMimeMessage()).thenAnswer(inv -> new MimeMessage((Session) null));
        mailService = new MailServiceImpl(sender);
        ReflectionTestUtils.setField(mailService, "from", "no-reply@test.local");
    }

    private MimeMessage sent() {
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender).send(captor.capture());
        return captor.getValue();
    }

    @Test
    void loginCodeEmailContainsCodeRecipientAndSubject() throws Exception {
        mailService.sendLoginCode("user@test.com", "Ana", "123456", 10);

        MimeMessage message = sent();
        assertEquals("user@test.com", message.getRecipients(Message.RecipientType.TO)[0].toString());
        assertEquals("no-reply@test.local", message.getFrom()[0].toString());
        assertEquals("Tu código de verificación", message.getSubject());
        String content = (String) message.getContent();
        assertTrue(content.contains("123456"));
        assertTrue(content.contains("10 minutos"));
        assertFalse(content.contains("{{"), "no deben quedar marcadores sin reemplazar");
    }

    @Test
    void userSuppliedNameIsHtmlEscaped() throws Exception {
        mailService.sendLoginCode("user@test.com", "<script>alert(1)</script>", "123456", 10);

        String content = (String) sent().getContent();
        assertFalse(content.contains("<script>"));
        assertTrue(content.contains("&lt;script&gt;"));
    }

    @Test
    void passwordResetEmailContainsLink() throws Exception {
        mailService.sendPasswordReset("user@test.com", "Ana",
                "http://localhost:5173/auth/reset?token=abc", 30);

        String content = (String) sent().getContent();
        assertTrue(content.contains("reset?token=abc"));
        assertTrue(content.contains("30 minutos"));
    }

    @Test
    void smtpFailureBecomesServiceUnavailable() {
        doThrow(new MailSendException("boom")).when(sender).send(any(MimeMessage.class));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> mailService.sendEnableTwoFactorCode("user@test.com", "Ana", "123456", 10));
        assertEquals(503, ex.getStatusCode().value());
        assertFalse(String.valueOf(ex.getReason()).contains("boom"));
    }

    @Test
    void usesBrevoApiInsteadOfSmtpWhenEnabled() throws Exception {
        BrevoApiClient brevo = mock(BrevoApiClient.class);
        when(brevo.isEnabled()).thenReturn(true);
        ReflectionTestUtils.setField(mailService, "brevo", brevo);
        ReflectionTestUtils.setField(mailService, "fromName", "Multiservicios Wilson");

        mailService.sendLoginCode("user@test.com", "Ana", "123456", 10);

        verify(brevo).send(eq("no-reply@test.local"), eq("Multiservicios Wilson"), eq("user@test.com"),
                eq("Tu código de verificación"), contains("123456"));
        verify(sender, never()).send(any(MimeMessage.class));
    }

    @Test
    void brevoApiFailureBecomesServiceUnavailable() throws Exception {
        BrevoApiClient brevo = mock(BrevoApiClient.class);
        when(brevo.isEnabled()).thenReturn(true);
        doThrow(new java.io.IOException("Brevo respondió 401")).when(brevo).send(any(), any(), any(), any(), any());
        ReflectionTestUtils.setField(mailService, "brevo", brevo);
        ReflectionTestUtils.setField(mailService, "fromName", "X");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> mailService.sendLoginCode("user@test.com", "Ana", "123456", 10));
        assertEquals(503, ex.getStatusCode().value());
        assertFalse(String.valueOf(ex.getReason()).contains("401"));
    }
}
