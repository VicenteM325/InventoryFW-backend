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

    private static com.vm325.inventory_back.dtos.SaleResponseDto sale() {
        return com.vm325.inventory_back.dtos.SaleResponseDto.builder()
                .salesId(42L).employeeUsername("empleado1").customerName("Ana")
                .date(java.time.LocalDateTime.of(2026, 3, 5, 14, 30))
                .total(new java.math.BigDecimal("2495.00"))
                .items(java.util.List.of(
                        com.vm325.inventory_back.dtos.SaleItemResponseDto.builder()
                                .productId(1L).productName("<b>Funda</b> & cable").quantity(2)
                                .unitPrice(new java.math.BigDecimal("45.00")).subtotal(new java.math.BigDecimal("90.00")).build(),
                        com.vm325.inventory_back.dtos.SaleItemResponseDto.builder()
                                .productId(2L).productName("Samsung A54").quantity(1)
                                .unitPrice(new java.math.BigDecimal("2405.00")).subtotal(new java.math.BigDecimal("2405.00")).build()))
                .build();
    }

    @Test
    void receiptEmailListsItemsTotalAndEscapesProductNames() throws Exception {
        mailService.sendSaleReceipt("cliente@test.com", sale());

        MimeMessage message = sent();
        assertEquals("cliente@test.com", message.getRecipients(Message.RecipientType.TO)[0].toString());
        assertEquals("Recibo de tu compra No. 42", message.getSubject());
        String content = (String) message.getContent();
        assertTrue(content.contains("Samsung A54"));
        assertTrue(content.contains("Q 2,495.00"), "total");
        assertTrue(content.contains("Q 90.00"), "subtotal");
        assertTrue(content.contains("05/03/2026 14:30"));
        assertTrue(content.contains("&lt;b&gt;Funda&lt;/b&gt; &amp; cable"), "el nombre del producto se escapa");
        assertFalse(content.contains("<b>Funda"));
        assertFalse(content.contains("{{"), "no deben quedar marcadores sin reemplazar");
        assertTrue(content.contains("Gracias por tu compra"));
        assertFalse(content.contains("ignora este mensaje"), "el pie de seguridad no aplica a un recibo");
    }

    private static MailService.SupplierOrder order() {
        return new MailService.SupplierOrder("ventas@tigo.com", "Tigo <S.A.>", "Funda <b>x</b>", "750100",
                20, 3, 3);
    }

    @Test
    void supplierOrderPreviewHasRecipientSubjectQuantityAndEscapedValues() {
        ReflectionTestUtils.setField(mailService, "businessName", "Multiservicios Wilson");
        ReflectionTestUtils.setField(mailService, "businessPhone", "5555-1234");

        MailService.RenderedMail mail = mailService.previewSupplierOrder(order());

        assertEquals("ventas@tigo.com", mail.to());
        assertEquals("Nuevo Pedido: Funda <b>x</b> (20 unidades)", mail.subject());
        assertTrue(mail.html().contains("20 unidades"));
        assertTrue(mail.html().contains("750100"));
        assertTrue(mail.html().contains("Funda &lt;b&gt;x&lt;/b&gt;"), "el producto se escapa");
        assertTrue(mail.html().contains("Tigo &lt;S.A.&gt;"), "el proveedor se escapa");
        assertTrue(mail.html().contains("Tel. 5555-1234"));
        assertTrue(mail.html().contains("<h2 style=\"margin:0 0 16px;color:#1f2937;\">Nuevo Pedido</h2>"), "título del correo");
        assertFalse(mail.html().contains("Existencia actual"), "no se muestra el stock del negocio al proveedor");
        assertFalse(mail.html().contains("{{"), "no deben quedar marcadores sin reemplazar");
        assertFalse(mail.html().contains("ignora este mensaje"));
        verify(sender, never()).send(any(MimeMessage.class));
    }

    @Test
    void supplierOrderIsSentWithReplyToTheBusiness() throws Exception {
        ReflectionTestUtils.setField(mailService, "businessName", "Multiservicios Wilson");
        ReflectionTestUtils.setField(mailService, "businessPhone", "");
        ReflectionTestUtils.setField(mailService, "replyTo", "dueno@gmail.com");

        mailService.sendSupplierOrder(order());

        MimeMessage message = sent();
        assertEquals("ventas@tigo.com", message.getRecipients(Message.RecipientType.TO)[0].toString());
        assertEquals("dueno@gmail.com", message.getReplyTo()[0].toString());
        assertFalse(((String) message.getContent()).contains("Tel."));
    }

    @Test
    void supplierOrderRepliesToTheSenderWhenNoReplyToIsConfigured() throws Exception {
        ReflectionTestUtils.setField(mailService, "businessName", "Multiservicios Wilson");
        ReflectionTestUtils.setField(mailService, "businessPhone", "");

        mailService.sendSupplierOrder(order());

        assertEquals("no-reply@test.local", sent().getReplyTo()[0].toString());
    }

    @Test
    void supplierOrderViaBrevoCarriesReplyTo() throws Exception {
        BrevoApiClient brevo = mock(BrevoApiClient.class);
        when(brevo.isEnabled()).thenReturn(true);
        ReflectionTestUtils.setField(mailService, "brevo", brevo);
        ReflectionTestUtils.setField(mailService, "fromName", "Multiservicios Wilson");
        ReflectionTestUtils.setField(mailService, "businessName", "Multiservicios Wilson");
        ReflectionTestUtils.setField(mailService, "businessPhone", "");
        ReflectionTestUtils.setField(mailService, "replyTo", "dueno@gmail.com");

        mailService.sendSupplierOrder(order());

        verify(brevo).send(eq("no-reply@test.local"), eq("Multiservicios Wilson"), eq("ventas@tigo.com"),
                contains("Nuevo Pedido"), contains("20 unidades"), eq("dueno@gmail.com"));
        verify(sender, never()).send(any(MimeMessage.class));
    }
}
