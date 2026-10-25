package com.vm325.inventory_back.jwt;


import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private int expiration;

    // HS256 exige una clave de al menos 256 bits; con una más corta fallaba en
    // cada login. Se valida al arrancar para fallar rápido y con mensaje claro.
    @PostConstruct
    void validateSecret() {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT_SECRET debe tener al menos 32 caracteres");
        }
    }

    /** Duración del JWT en segundos; la cookie de sesión usa el mismo valor. */
    public int getExpirationSeconds() {
        return expiration;
    }

    private static final String PURPOSE_CLAIM = "purpose";
    private static final String PURPOSE_2FA = "2fa";
    private static final long CHALLENGE_TTL_MS = 10 * 60 * 1000L;

    public String generateToken(Authentication authentication){
        UserDetails mainUser = (UserDetails) authentication.getPrincipal();
        return generateToken(mainUser.getUsername());
    }

    /** Token de sesión (el que viaja en la cookie jwt). */
    public String generateToken(String userName){
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder().setSubject(userName)
                .setIssuedAt(new Date())
                .setExpiration(new Date(new Date().getTime() + expiration * 1000L))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Token efímero que acredita que la contraseña ya fue validada y que falta
     * el segundo factor. No sirve como sesión: validateToken lo rechaza.
     */
    public String generateChallengeToken(String userName){
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder().setSubject(userName)
                .claim(PURPOSE_CLAIM, PURPOSE_2FA)
                .setIssuedAt(new Date())
                .setExpiration(new Date(new Date().getTime() + CHALLENGE_TTL_MS))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /** Devuelve el usuario de un challenge válido; lanza JwtException si es inválido, vencido o de otro tipo. */
    public String extractChallengeUser(String token){
        Claims claims = extractAllClaims(token);
        if (!PURPOSE_2FA.equals(claims.get(PURPOSE_CLAIM, String.class))) {
            throw new io.jsonwebtoken.JwtException("Token de verificación inválido");
        }
        return claims.getSubject();
    }

    public Boolean validateToken(String token, UserDetails userDetails){
        // Un challenge de 2FA nunca debe aceptarse como sesión.
        if (extractAllClaims(token).get(PURPOSE_CLAIM) != null) {
            return false;
        }
        final String userName = extractUserName(token);
        return (userName.equals(userDetails.getUsername()) && !isTokenExpired(token));
    }

    public Boolean isTokenExpired(String token){
        return extractExpiration(token).before(new Date());
    }

    public Date extractExpiration(String token){
        return extractAllClaims(token).getExpiration();
    }
    public Claims extractAllClaims(String token){
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public String extractUserName(String token){
        return extractAllClaims(token).getSubject();
    }
}