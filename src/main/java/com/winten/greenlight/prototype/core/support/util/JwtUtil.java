package com.winten.greenlight.prototype.core.support.util;

import com.winten.greenlight.prototype.core.domain.customer.CustomerEntry;
import com.winten.greenlight.prototype.core.domain.customer.EntryTicket;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Component
public class JwtUtil {
    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration:86400000}") // 24시간 (밀리초)
    private Long expiration;

    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        return Keys.hmacShaKeyFor(keyBytes);
    }
    // TODO
    // UserInfo로부터 JWT 토큰 생성
    public String generateToken(CustomerEntry entry) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("actionId", entry.getActionId());
        claims.put("customerId", entry.getCustomerId());
        claims.put("destinationUrl", entry.getDestinationUrl());
        claims.put("timestamp", entry.getTimestamp());

        return createToken(claims, entry.getCustomerId());
    }

    // Claims와 subject로 토큰 생성
    private String createToken(Map<String, Object> claims, String subject) {
        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey(), Jwts.SIG.HS256)
                .compact();
    }

    // 토큰에서 사용자명 추출
    public String extractCustomerId(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    // 토큰에서 만료일 추출
    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    // 토큰에서 특정 클레임 추출
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    // 토큰에서 모든 클레임 추출
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    // 토큰 만료 확인
    private Boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    // 토큰 유효성 검증 (UserInfo 없이)
    public Boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token);
            return !isTokenExpired(token);
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    // 토큰에서 CustomerEntry 객체 생성
    public EntryTicket getEntryTicketFromToken(String token) {
        Claims claims = extractAllClaims(token);

        return EntryTicket.builder()
                .actionId(claims.get("actionId", Long.class))
                .customerId(claims.get("customerId", String.class))
                .destinationUrl(claims.get("destinationUrl", String.class))
                .timestamp(claims.get("timestamp", Long.class))
                .build();
    }

    // 토큰에서 특정 클레임 값 추출
    public String getClaimFromToken(String token, String claimName) {
        Claims claims = extractAllClaims(token);
        return claims.get(claimName, String.class);
    }

    // 토큰 만료까지 남은 시간 (밀리초)
    public Long getExpirationTime(String token) {
        Date expiration = extractExpiration(token);
        return expiration.getTime() - System.currentTimeMillis();
    }

    /**
     * JWT 토큰에서 actionId를 추출합니다.
     *
     * @param token JWT 토큰 문자열
     * @return 추출된 actionId (없으면 null)
     */
    public Long extractActionId(String token) {
        try {
            Claims claims = extractAllClaims(token);
            return claims.get("actionId", Long.class);
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }
}