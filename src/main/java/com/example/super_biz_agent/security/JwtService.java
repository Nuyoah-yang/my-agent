package com.example.super_biz_agent.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

@Service
public class JwtService {

    @Value("${jwt.secret-key}")
    private String secretKey;

    @Value("${jwt.issuer}")
    private String issuer;

    @Value("${jwt.access-token-expire-minutes:120}")
    private long accessTokenExpireMinutes;

    public String generateAccessToken(Long userId, String username) {
        // Token 的 subject 存 userId，业务侧用它做会话归属校验
        Instant now = Instant.now();
        Instant expireAt = now.plusSeconds(accessTokenExpireMinutes * 60);
        return Jwts.builder()
                .issuer(issuer)
                .subject(String.valueOf(userId))
                .issuedAt(Date.from(now))
                .expiration(Date.from(expireAt))
                .claims(Map.of("username", username))
                .signWith(getSigningKey())
                .compact();
    }

    public Claims parseClaims(String token) {
        // 统一在这里做签名校验和 claims 解析，避免业务层重复逻辑
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Long extractUserId(String token) {
        Claims claims = parseClaims(token);
        return Long.parseLong(claims.getSubject());
    }

    public long getAccessTokenExpireSeconds() {
        return accessTokenExpireMinutes * 60;
    }

    private SecretKey getSigningKey() {
        // HMAC 密钥长度需满足算法要求，配置里建议使用足够长的随机串
        byte[] bytes = secretKey.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(bytes);
    }
}
