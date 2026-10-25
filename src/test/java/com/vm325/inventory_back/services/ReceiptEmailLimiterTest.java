package com.vm325.inventory_back.services;

import com.vm325.inventory_back.support.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class ReceiptEmailLimiterTest {

    private MutableClock clock;
    private ReceiptEmailLimiter limiter;

    @BeforeEach
    void setUp() {
        clock = new MutableClock();
        limiter = new ReceiptEmailLimiter(clock);
        ReflectionTestUtils.setField(limiter, "maxPerSeller", 5);
        ReflectionTestUtils.setField(limiter, "maxPerRecipient", 2);
    }

    @Test
    void limitsPerRecipientIgnoringCaseAndSpaces() {
        assertTrue(limiter.tryAcquire("emp1", "ana@test.com"));
        assertTrue(limiter.tryAcquire("emp1", " ANA@test.com "));
        assertFalse(limiter.tryAcquire("emp1", "ana@test.com"));
        assertTrue(limiter.tryAcquire("emp1", "otro@test.com"));
    }

    @Test
    void limitsPerSellerAndRejectedAttemptsDoNotCount() {
        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.tryAcquire("emp1", "c" + i + "@test.com"));
        }
        assertFalse(limiter.tryAcquire("emp1", "nuevo@test.com"));
        assertTrue(limiter.tryAcquire("emp2", "nuevo@test.com"), "otro empleado no se ve afectado");
    }

    @Test
    void windowSlidesAfterAnHour() {
        assertTrue(limiter.tryAcquire("emp1", "ana@test.com"));
        assertTrue(limiter.tryAcquire("emp1", "ana@test.com"));
        assertFalse(limiter.tryAcquire("emp1", "ana@test.com"));

        clock.advance(Duration.ofMinutes(61));

        assertTrue(limiter.tryAcquire("emp1", "ana@test.com"));
    }
}
