package com.vm325.inventory_back.services;

import com.vm325.inventory_back.dtos.SaleItemResponseDto;
import com.vm325.inventory_back.dtos.SaleResponseDto;
import com.vm325.inventory_back.reports.ReportFormats;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Service
public class MailServiceImpl implements MailService {

    private static final Logger log = LoggerFactory.getLogger(MailServiceImpl.class);
    private static final String BRAND = "Inventory FW.";

    private final JavaMailSender mailSender;

    @Value("${mail.from}")
    private String from;

    @Value("${mail.from-name:Multiservicios Wilson}")
    private String fromName;

    @Value("${mail.reply-to:}")
    private String replyTo;

    @Value("${business.name:Multiservicios Wilson}")
    private String businessName;

    @Value("${business.phone:}")
    private String businessPhone;

    // Opcional: con BREVO_API_KEY el envío va por HTTPS en lugar de SMTP.
    @Autowired(required = false)
    private BrevoApiClient brevo;

    @Autowired
    public MailServiceImpl(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public void sendLoginCode(String to, String name, String code, int validMinutes) {
        String body = render("code", Map.of(
                "intro", "Usa este código para completar tu inicio de sesión:",
                "code", code,
                "minutes", String.valueOf(validMinutes)));
        send(to, "Tu código de verificación", "Verificación en dos pasos", name, body);
    }

    @Override
    public void sendEnableTwoFactorCode(String to, String name, String code, int validMinutes) {
        String body = render("code", Map.of(
                "intro", "Usa este código para activar la verificación en dos pasos en tu cuenta:",
                "code", code,
                "minutes", String.valueOf(validMinutes)));
        send(to, "Activa la verificación en dos pasos", "Activar verificación en dos pasos", name, body);
    }

    @Override
    public void sendPasswordReset(String to, String name, String link, int validMinutes) {
        String body = render("reset", Map.of(
                "link", link,
                "minutes", String.valueOf(validMinutes)));
        send(to, "Restablece tu contraseña", "Recuperación de contraseña", name, body);
    }

    @Override
    public void sendSaleReceipt(String to, SaleResponseDto sale) {
        StringBuilder rows = new StringBuilder();
        for (SaleItemResponseDto item : sale.getItems()) {
            rows.append("<tr style=\"border-bottom:1px solid #e5e7eb;\">")
                    .append("<td style=\"padding:6px 0;\">").append(HtmlUtils.htmlEscape(item.getProductName())).append("</td>")
                    .append("<td align=\"right\" style=\"padding:6px 0;\">").append(item.getQuantity()).append("</td>")
                    .append("<td align=\"right\" style=\"padding:6px 0;\">").append(ReportFormats.money(item.getUnitPrice())).append("</td>")
                    .append("<td align=\"right\" style=\"padding:6px 0;\">").append(ReportFormats.money(item.getSubtotal())).append("</td>")
                    .append("</tr>");
        }
        String body = render("receipt", Map.of(
                "saleId", String.valueOf(sale.getSalesId()),
                "date", sale.getDate().format(ReportFormats.DATE_TIME),
                "employee", sale.getEmployeeUsername(),
                "rows", rows.toString(),
                "total", ReportFormats.money(sale.getTotal())), "rows");
        String name = sale.getCustomerName() == null || sale.getCustomerName().isBlank() ? "cliente" : sale.getCustomerName();
        send(to, "Recibo de tu compra No. " + sale.getSalesId(), "Recibo de compra", name, body,
                "Gracias por tu compra. " + BRAND);
    }

    @Override
    public RenderedMail previewSupplierOrder(SupplierOrder order) {
        String phoneLine = businessPhone == null || businessPhone.isBlank()
                ? "" : "<br>Tel. " + HtmlUtils.htmlEscape(businessPhone);
        String body = render("supplier-order", Map.of(
                "product", order.productName(),
                "barcode", order.barcode() == null || order.barcode().isBlank() ? "-" : order.barcode(),
                "quantity", String.valueOf(order.quantity()),
                "stock", String.valueOf(order.currentStock()),
                "minStock", String.valueOf(order.minStock()),
                "business", businessName,
                "phoneLine", phoneLine), "phoneLine");
        String subject = "Pedido de reposición: " + order.productName() + " (" + order.quantity() + " unidades)";
        String html = renderLayout("Pedido de reposición", order.supplierName(), body,
                "Pedido enviado desde el sistema de inventario de " + businessName + ".");
        return new RenderedMail(order.to(), subject, html);
    }

    @Override
    public void sendSupplierOrder(SupplierOrder order) {
        RenderedMail mail = previewSupplierOrder(order);
        deliver(mail.to(), mail.subject(), mail.html(), replyTo == null || replyTo.isBlank() ? from : replyTo);
    }

    private void send(String to, String subject, String title, String name, String body) {
        send(to, subject, title, name, body, "Si no solicitaste esto, ignora este mensaje. " + BRAND);
    }

    private void send(String to, String subject, String title, String name, String body, String footer) {
        deliver(to, subject, renderLayout(title, name, body, footer), null);
    }

    private String renderLayout(String title, String name, String body, String footer) {
        return render("layout", Map.of("title", title, "name", name, "body", body, "footer", footer), "body");
    }

    /** Entrega por la API de Brevo si está activa, o por SMTP; replyTo es opcional. */
    private void deliver(String to, String subject, String html, String replyTo) {
        try {
            if (brevo != null && brevo.isEnabled()) {
                if (replyTo == null) {
                    brevo.send(from, fromName, to, subject, html);
                } else {
                    brevo.send(from, fromName, to, subject, html, replyTo);
                }
                return;
            }
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            if (replyTo != null) {
                helper.setReplyTo(replyTo);
            }
            helper.setText(html, true);
            mailSender.send(message);
        } catch (MessagingException | MailException | IOException e) {
            log.error("No se pudo enviar el correo '{}' a {}: {}", subject, to, e.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "No se pudo enviar el correo. Intente de nuevo más tarde");
        }
    }

    /** Reemplaza {{clave}}; las claves listadas en rawKeys se insertan sin escapar (HTML ya renderizado). */
    private String render(String template, Map<String, String> values, String... rawKeys) {
        String html = load(template);
        for (Map.Entry<String, String> entry : values.entrySet()) {
            boolean raw = java.util.Arrays.asList(rawKeys).contains(entry.getKey());
            String value = raw ? entry.getValue() : HtmlUtils.htmlEscape(entry.getValue());
            html = html.replace("{{" + entry.getKey() + "}}", value);
        }
        return html;
    }

    private String load(String template) {
        try {
            return new ClassPathResource("mail/" + template + ".html")
                    .getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
