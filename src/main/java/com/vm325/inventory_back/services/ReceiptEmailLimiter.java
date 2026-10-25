package com.vm325.inventory_back.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limita los recibos por correo en una ventana de una hora: por empleado que vende y por
 * destinatario. El correo lo escribe un empleado a mano, así que sin tope el endpoint serviría
 * para mandar correo a direcciones arbitrarias. En memoria (un solo nodo).
 */
@Service
public class ReceiptEmailLimiter {

    private static final Duration WINDOW = Duration.ofHours(1);

    private final ConcurrentHashMap<String, Deque<Instant>> sends = new ConcurrentHashMap<>();
    private final Clock clock;

    @Value("${receipt.max-per-seller-hour:20}")
    private int maxPerSeller;

    @Value("${receipt.max-per-recipient-hour:3}")
    private int maxPerRecipient;

    @Autowired
    public ReceiptEmailLimiter(Clock clock) {
        this.clock = clock;
    }

    /** true y registra el envío si cabe en ambos límites; false si alguno ya se alcanzó (no registra nada). */
    public synchronized boolean tryAcquire(String seller, String recipient) {
        Instant now = clock.instant();
        String sellerKey = "s:" + normalize(seller);
        String recipientKey = "r:" + normalize(recipient);
        if (count(sellerKey, now) >= maxPerSeller || count(recipientKey, now) >= maxPerRecipient) {
            return false;
        }
        sends.computeIfAbsent(sellerKey, k -> new ArrayDeque<>()).add(now);
        sends.computeIfAbsent(recipientKey, k -> new ArrayDeque<>()).add(now);
        return true;
    }

    private int count(String key, Instant now) {
        Deque<Instant> times = sends.get(key);
        if (times == null) {
            return 0;
        }
        Instant cutoff = now.minus(WINDOW);
        while (!times.isEmpty() && times.peekFirst().isBefore(cutoff)) {
            times.pollFirst();
        }
        if (times.isEmpty()) {
            sends.remove(key);
            return 0;
        }
        return times.size();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
