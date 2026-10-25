package com.vm325.inventory_back.services;

import com.vm325.inventory_back.dtos.SaleResponseDto;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Envía el recibo de una venta ya confirmada. Nunca lanza: un fallo de correo o un límite
 * alcanzado solo se refleja en el estado devuelto, la venta ya quedó registrada.
 */
@Service
@RequiredArgsConstructor
public class SaleReceiptService {

    public static final String SENT = "ENVIADO";
    public static final String NOT_SENT = "NO_ENVIADO";
    public static final String SKIPPED = "OMITIDO";

    private static final Logger log = LoggerFactory.getLogger(SaleReceiptService.class);

    private final MailService mailService;
    private final ReceiptEmailLimiter limiter;

    public String sendReceipt(SaleResponseDto sale, String email, String seller) {
        if (email == null || email.isBlank()) {
            return SKIPPED;
        }
        String to = email.trim();
        if (!limiter.tryAcquire(seller, to)) {
            log.warn("Recibo de la venta {} no enviado: límite de envíos alcanzado", sale.getSalesId());
            return NOT_SENT;
        }
        try {
            mailService.sendSaleReceipt(to, sale);
            return SENT;
        } catch (RuntimeException e) {
            log.error("No se pudo enviar el recibo de la venta {}: {}", sale.getSalesId(), e.getMessage());
            return NOT_SENT;
        }
    }
}
