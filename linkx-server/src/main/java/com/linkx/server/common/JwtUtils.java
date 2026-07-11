package com.linkx.server.common;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
public class JwtUtils {

    @Value("${linkx.jwt.secret}")
    private String secret;

    @Value("${linkx.jwt.access-expire}")
    private Long accessExpire;

    @Value("${linkx.jwt.refresh-expire}")
    private Long refreshExpire;

    private SecretKey getSecretKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(Long userId, String username) {
        return generateToken(userId, username, TokenType.ACCESS, java.util.UUID.randomUUID().toString(), accessExpire);
    }

    public String generateRefreshToken(Long userId, String username) {
        return generateToken(userId, username, TokenType.REFRESH, java.util.UUID.randomUUID().toString(), refreshExpire);
    }

    public String generateToken(Long userId, String username, TokenType tokenType, String jti, Long expireTime) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("username", username);
        claims.put("type", tokenType.value());
        claims.put("jti", jti);

        Date now = new Date();
        Date expiration = new Date(now.getTime() + expireTime);

        return Jwts.builder()
                .claims(claims)
                .id(jti)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(getSecretKey())
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getSecretKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Long getUserIdFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get("userId", Long.class);
    }

    public TokenType getTokenType(String token) {
        Claims claims = parseToken(token);
        return TokenType.fromClaim(claims.get("type", String.class));
    }
}
