package com.vm325.inventory_back.services;

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

    private final JavaMailSender mailSender;

    @Value("${mail.from}")
    private String from;

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

    private void send(String to, String subject, String title, String name, String body) {
        String html = render("layout", Map.of("title", title, "name", name, "body", body), "body");
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
        } catch (MessagingException | MailException e) {
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
