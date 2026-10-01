package com.gymfit.checkin;

import com.gymfit.common.error.BadRequestException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class QrTokenService {

    @Value("${gymfit.qr.secret}")
    private String secret;

    @Value("${gymfit.qr.ttl-seconds}")
    private long ttlSeconds;

    private SecretKey key;

    @PostConstruct
    public void initialize() {
        key = Keys.hmacShaKeyFor(
                secret.getBytes(StandardCharsets.UTF_8)
        );
    }

    public IssuedQrToken issue(Long memberId) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(ttlSeconds);
        String jti = UUID.randomUUID().toString();

        String token = Jwts.builder()
                .id(jti)
                .subject(memberId.toString())
                .claim("memberId", memberId)
                .claim("purpose", "MEMBER_QR")
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();

        return new IssuedQrToken(
                token,
                expiresAt
        );
    }

    public QrTokenPayload parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String purpose = claims.get(
                    "purpose",
                    String.class
            );

            if (!"MEMBER_QR".equals(purpose)) {
                throw invalidQr();
            }

            String jti = claims.getId();
            String subject = claims.getSubject();

            if (jti == null || subject == null) {
                throw invalidQr();
            }

            Long subjectMemberId = Long.valueOf(subject);
            Number claimMemberId = claims.get(
                    "memberId",
                    Number.class
            );

            if (claimMemberId == null
                    || claimMemberId.longValue() != subjectMemberId) {
                throw invalidQr();
            }

            return new QrTokenPayload(
                    jti,
                    subjectMemberId
            );
        } catch (BadRequestException exception) {
            throw exception;
        } catch (JwtException | IllegalArgumentException exception) {
            throw new BadRequestException(
                    "qr_invalid_or_expired",
                    "QR không hợp lệ hoặc đã hết hạn"
            );
        }
    }

    private BadRequestException invalidQr() {
        return new BadRequestException(
                "qr_invalid",
                "QR không hợp lệ"
        );
    }

    public record IssuedQrToken(
            String token,
            Instant expiresAtUtc
    ) {
    }
}