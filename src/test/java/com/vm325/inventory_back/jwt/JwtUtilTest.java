package com.vm325.inventory_back.jwt;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {

    private JwtUtil jwtUtil;
    private UserDetails ana;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", "0123456789abcdef0123456789abcdef");
        ReflectionTestUtils.setField(jwtUtil, "expiration", 3600);
        ana = new org.springframework.security.core.userdetails.User("ana", "x",
                List.of(new SimpleGrantedAuthority("ROLE_EMPLOYEE")));
    }

    @Test
    void sessionTokenValidatesForItsUser() {
        String token = jwtUtil.generateToken("ana");

        assertTrue(jwtUtil.validateToken(token, ana));
    }

    @Test
    void challengeTokenIsNeverAValidSession() {
        String challenge = jwtUtil.generateChallengeToken("ana");

        assertFalse(jwtUtil.validateToken(challenge, ana));
        assertEquals("ana", jwtUtil.extractChallengeUser(challenge));
    }

    @Test
    void sessionTokenIsNotAcceptedAsChallenge() {
        String session = jwtUtil.generateToken("ana");

        assertThrows(JwtException.class, () -> jwtUtil.extractChallengeUser(session));
    }

    @Test
    void tamperedTokenIsRejected() {
        String challenge = jwtUtil.generateChallengeToken("ana");

        assertThrows(JwtException.class, () -> jwtUtil.extractChallengeUser(challenge + "x"));
    }

    @Test
    void shortSecretFailsFastAtStartup() {
        ReflectionTestUtils.setField(jwtUtil, "secret", "corto");

        assertThrows(IllegalStateException.class, () -> ReflectionTestUtils.invokeMethod(jwtUtil, "validateSecret"));
    }
}
