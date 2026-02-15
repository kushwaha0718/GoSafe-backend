package com.gosafe.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    @Value("${gosafe.jwt.secret}")
    private String secret;

    @Value("${gosafe.jwt.expiration}")
    private long expiration;   // milliseconds — 604800000 = 7 days

    private SecretKey key() {
        // Pad secret to at least 64 bytes for HS512
        String padded = secret;
        while (padded.getBytes(StandardCharsets.UTF_8).length < 64) padded += secret;
        return Keys.hmacShaKeyFor(padded.getBytes(StandardCharsets.UTF_8));
    }

    /** Generate token — payload: { id, email, name } */
    public String generateToken(Long userId, String email, String name) {
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("email", email)
                .claim("name", name)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(key())
                .compact();
    }

    /** Extract userId from token */
    public Long getUserId(String token) {
        return Long.parseLong(getClaims(token).getSubject());
    }

    /** Validate and return claims — throws JwtException if invalid/expired */
    public Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(key())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isValid(String token) {
        try { getClaims(token); return true; }
        catch (JwtException | IllegalArgumentException e) { return false; }
    }
}
