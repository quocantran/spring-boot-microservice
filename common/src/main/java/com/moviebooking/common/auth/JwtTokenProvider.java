package com.moviebooking.common.auth;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

@Component
public class JwtTokenProvider {

    @Value("${jwt.secret:movie-booking-jwt-secret-key-2026}")
    private String jwtSecret;

    // Default expiration: 7 days (604,800,000 ms) matching standard NestJS JWT default
    private final long jwtExpirationMs = 7 * 24 * 60 * 60 * 1000L;

    public String generateToken(String sub, String email, String name, String role) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationMs);

        return Jwts.builder()
                .subject(sub)
                .claim("email", email)
                .claim("name", name)
                .claim("role", role != null ? role : "USER")
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key)
                .compact();
    }
}
