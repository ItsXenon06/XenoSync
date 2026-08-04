package com.xenosync.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private static final String CLAIM_TYPE = "type";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    private final SecretKey signingKey;
    private final long accessTokenExpiryMinutes;
    private final long refreshTokenExpiryDays;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiry-minutes}") long accessTokenExpiryMinutes,
            @Value("${jwt.refresh-token-expiry-days}") long refreshTokenExpiryDays
    ) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes());
        this.accessTokenExpiryMinutes = accessTokenExpiryMinutes;
        this.refreshTokenExpiryDays = refreshTokenExpiryDays;
    }

    public String generateAccessToken(UUID userId) {
        return buildToken(userId, TYPE_ACCESS, Instant.now().plusSeconds(accessTokenExpiryMinutes * 60));
    }

    public String generateRefreshToken(UUID userId) {
        return buildToken(userId, TYPE_REFRESH, Instant.now().plusSeconds(refreshTokenExpiryDays * 86400));
    }

    private String buildToken(UUID userId, String type, Instant expiry) {
        return Jwts.builder()
                .subject(userId.toString())
                .claim(CLAIM_TYPE, type)
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(expiry))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Validates an access token and returns the userId. Throws JwtException
     * (or a subclass) on any failure: expired, malformed, bad signature,
     * or wrong token type (e.g. a refresh token presented here).
     */
    public UUID validateAccessToken(String token) {
        return validateAndExtract(token, TYPE_ACCESS);
    }

    /**
     * Validates a refresh token and returns the userId. Note: this only
     * checks JWT-level validity (signature, expiry, type). It does NOT
     * check server-side revocation/rotation state — that's RefreshTokenService's
     * job, using the token's hash against refresh_tokens.
     */
    public UUID validateRefreshToken(String token) {
        return validateAndExtract(token, TYPE_REFRESH);
    }

    private UUID validateAndExtract(String token, String expectedType) {
        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException | UnsupportedJwtException | MalformedJwtException
                 | SignatureException | IllegalArgumentException e) {
            throw new JwtException("Invalid token", e);
        }

        String actualType = claims.get(CLAIM_TYPE, String.class);
        if (!expectedType.equals(actualType)) {
            throw new JwtException("Unexpected token type: expected " + expectedType + " but got " + actualType);
        }

        return UUID.fromString(claims.getSubject());
    }
}