package com.vm325.inventory_back.services;

import com.vm325.inventory_back.support.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class LoginAttemptServiceTest {

    private MutableClock clock;
    private LoginAttemptService service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock();
        service = new LoginAttemptService(clock);
        ReflectionTestUtils.setField(service, "maxFailures", 5);
        ReflectionTestUtils.setField(service, "windowMinutes", 15);
        ReflectionTestUtils.setField(service, "lockMinutes", 15);
    }

    @Test
    void locksAfterFiveFailures() {
        for (int i = 0; i < 4; i++) {
            service.recordFailure("ana");
        }
        assertEquals(0, service.secondsLocked("ana"));

        service.recordFailure("ana");

        assertTrue(service.secondsLocked("ana") > 0);
    }

    @Test
    void lockIsPerUserAndCaseInsensitive() {
        for (int i = 0; i < 5; i++) {
            service.recordFailure("Ana");
        }

        assertTrue(service.secondsLocked("ana") > 0);
        assertEquals(0, service.secondsLocked("luis"));
    }

    @Test
    void lockEndsAfterLockTime() {
        for (int i = 0; i < 5; i++) {
            service.recordFailure("ana");
        }

        clock.advance(Duration.ofMinutes(15));

        assertEquals(0, service.secondsLocked("ana"));
    }

    @Test
    void successResetsTheCounter() {
        for (int i = 0; i < 4; i++) {
            service.recordFailure("ana");
        }
        service.recordSuccess("ana");
        service.recordFailure("ana");

        assertEquals(0, service.secondsLocked("ana"));
    }

    @Test
    void failuresOutsideTheWindowDoNotAccumulate() {
        for (int i = 0; i < 4; i++) {
            service.recordFailure("ana");
        }
        clock.advance(Duration.ofMinutes(16));
        service.recordFailure("ana");

        assertEquals(0, service.secondsLocked("ana"));
    }
}
