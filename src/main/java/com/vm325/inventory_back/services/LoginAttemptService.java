package com.vm325.inventory_back.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Freno a la fuerza bruta sobre el login: tras N contraseñas incorrectas
 * seguidas para un usuario, se bloquean nuevos intentos durante un tiempo.
 * Vive en memoria (un solo nodo); se reinicia con el proceso. Se clava a la
 * cuenta y no a la IP para que un atacante detrás de proxys no lo evada.
 */
@Service
public class LoginAttemptService {

    private record Entry(int failures, Instant firstFailure, Instant lockedUntil) {
    }

    private final ConcurrentHashMap<String, Entry> attempts = new ConcurrentHashMap<>();
    private final Clock clock;

    @Value("${security.login.max-failures:5}")
    private int maxFailures;

    @Value("${security.login.window-minutes:15}")
    private int windowMinutes;

    @Value("${security.login.lock-minutes:15}")
    private int lockMinutes;

    @Autowired
    public LoginAttemptService(Clock clock) {
        this.clock = clock;
    }

    /** Segundos restantes de bloqueo (0 si el usuario puede intentar). */
    public long secondsLocked(String username) {
        Entry entry = attempts.get(key(username));
        if (entry == null || entry.lockedUntil() == null) {
            return 0;
        }
        long left = Duration.between(clock.instant(), entry.lockedUntil()).getSeconds();
        return Math.max(left, 0);
    }

    public void recordFailure(String username) {
        Instant now = clock.instant();
        attempts.compute(key(username), (k, current) -> {
            if (current == null || isExpired(current, now)) {
                current = new Entry(0, now, null);
            }
            int failures = current.failures() + 1;
            Instant lockedUntil = failures >= maxFailures ? now.plus(Duration.ofMinutes(lockMinutes)) : null;
            return new Entry(failures, current.firstFailure(), lockedUntil);
        });
        evictStale(now);
    }

    public void recordSuccess(String username) {
        attempts.remove(key(username));
    }

    /** Un bloqueo ya cumplido, o una ventana de fallos que venció sin llegar al límite. */
    private boolean isExpired(Entry entry, Instant now) {
        if (entry.lockedUntil() != null) {
            return !now.isBefore(entry.lockedUntil());
        }
        return now.isAfter(entry.firstFailure().plus(Duration.ofMinutes(windowMinutes)));
    }

    private void evictStale(Instant now) {
        if (attempts.size() < 1000) {
            return;
        }
        Instant cutoff = now.minus(Duration.ofMinutes((long) windowMinutes + lockMinutes));
        attempts.values().removeIf(e -> e.firstFailure().isBefore(cutoff));
    }

    private String key(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }
}
