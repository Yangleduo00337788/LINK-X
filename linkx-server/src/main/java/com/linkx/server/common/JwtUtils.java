package com.linkx.server.common;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class JwtUtils {

    private static final int MIN_SECRET_BITS = 256;
    private static final Set<String> WEAK_SECRET_DENYLIST = new HashSet<>(List.of(
            "password", "secret", "123456", "12345678", "123456789",
            "qwerty", "abc123", "admin", "letmein", "welcome",
            "monkey", "dragon", "master", "login", "passw0rd",
            "1234567890", "123456789012345678901234567890",
            "changeme", "default", "null", "none", "undefined"
    ));

    @Value("${linkx.jwt.secret}")
    private String secret;

    @Value("${linkx.jwt.access-expire}")
    private Long accessExpire;

    @Value("${linkx.jwt.refresh-expire}")
    private Long refreshExpire;

    private SecretKey getSecretKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    @PostConstruct
    public void validateSecretStrength() {
        if (!StringUtils.hasText(secret)) {
            throw new IllegalStateException(
                    "[LinkX Security] linkx.jwt.secret 未配置。禁止在生产环境使用空 secret。");
        }
        if (secret.length() * 8 < MIN_SECRET_BITS) {
            throw new IllegalStateException(
                    "[LinkX Security] linkx.jwt.secret 长度不足（需要 ≥256bits，即 ≥32 字符）。当前: " + secret.length() + " 字符。");
        }
        String lower = secret.toLowerCase();
        if (WEAK_SECRET_DENYLIST.contains(lower) || lower.contains("password") || lower.contains("secret")) {
            throw new IllegalStateException(
                    "[LinkX Security] linkx.jwt.secret 检测到弱值或常见密码。禁止在生产环境使用弱 secret。");
        }
        long distinctBytes = secret.chars().distinct().count();
        if (distinctBytes < 16) {
            throw new IllegalStateException(
                    "[LinkX Security] linkx.jwt.secret 熵不足（需至少 16 种不同字符）。当前仅 " + distinctBytes + " 种。");
        }
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

        // 显式指定 HS256，避免依赖实现默认算法猜测
        return Jwts.builder()
                .claims(claims)
                .id(jti)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(getSecretKey(), Jwts.SIG.HS256)
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
        // 校验 token 类型必须为 ACCESS，防止 refresh token 被当作 access token 使用
        TokenType tokenType = TokenType.fromClaim(claims.get("type", String.class));
        if (tokenType != TokenType.ACCESS) {
            throw new io.jsonwebtoken.JwtException("access token 类型校验失败，拒绝非 access 类型令牌");
        }
        return claims.get("userId", Long.class);
    }

    public TokenType getTokenType(String token) {
        Claims claims = parseToken(token);
        return TokenType.fromClaim(claims.get("type", String.class));
    }

    public String getJtiFromToken(String token) {
        Claims claims = parseToken(token);
        String jti = claims.getId();
        if (!StringUtils.hasText(jti)) {
            jti = claims.get("jti", String.class);
        }
        if (!StringUtils.hasText(jti)) {
            throw new io.jsonwebtoken.JwtException("token 缺少 jti");
        }
        return jti.trim();
    }

    /**
     * 由 access token 的 jti 派生 API 签名密钥（hex，32 字节），登录时下发给前端。
     */
    public String deriveApiSignKeyHex(String jti) {
        if (!StringUtils.hasText(jti)) {
            throw new IllegalArgumentException("jti is required");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(getSecretKey());
            byte[] raw = mac.doFinal(("linkx-api-sign:" + jti.trim()).getBytes(StandardCharsets.UTF_8));
            return bytesToHex(raw);
        } catch (Exception e) {
            throw new IllegalStateException("derive api sign key failed", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
