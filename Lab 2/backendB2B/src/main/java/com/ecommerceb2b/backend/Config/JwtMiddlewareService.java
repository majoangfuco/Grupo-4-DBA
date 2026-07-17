package com.ecommerceb2b.backend.Config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtMiddlewareService {

    @Value("${jwt.secret:mySecureSecretKeyForJwtTokenGenerationAndValidation2024}")
    private String jwtSecret;

    @Value("${jwt.expiration:86400000}")
    private long jwtExpiration;

    public String generateToken(Long usuarioId, String correo, String rol) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes());
        Instant now = Instant.now();
        Instant expiresAt = now.plusMillis(jwtExpiration);
        
        return Jwts.builder()
                .subject(correo)
                .claim("usuario_id", usuarioId)
                .claim("rol", rol)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();
    }

    public boolean validateToken(String token) {
        try {
            SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes());
            Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public String getCorreoFromToken(String token) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes());
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.getSubject();
    }

    public Long getUsuarioIdFromToken(String token) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes());
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.get("usuario_id", Long.class);
    }

    public String getRolFromToken(String token) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes());
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.get("rol", String.class);
    }
}
