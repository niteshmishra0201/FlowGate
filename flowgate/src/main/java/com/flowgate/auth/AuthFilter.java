package com.flowgate.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Component
public class AuthFilter {

    private final SecretKey key;

    public AuthFilter(@Value("${flowgate.jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public Optional<String> validateAndExtractClientId(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return Optional.empty();
        }
        String token = authHeader.substring("Bearer ".length());

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            // "sub" (subject) is the standard JWT claim for "who does this token belong to"
            return Optional.ofNullable(claims.getSubject());
        } catch (JwtException e) {
            // Covers expired tokens, bad signatures, malformed tokens — all treated as "not valid"
            return Optional.empty();
        }
    }
}