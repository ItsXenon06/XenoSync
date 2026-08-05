package com.xenosync.service;

import com.xenosync.model.RefreshToken;
import com.xenosync.repository.RefreshTokenRepository;
import com.xenosync.security.JwtService;
import io.jsonwebtoken.JwtException;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Transactional
@Service
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final long refreshTokenExpiryDays;
    private final long absoluteExpiryDays;

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            JwtService jwtService,
            @Value("${jwt.refresh-token-expiry-days}") long refreshTokenExpiryDays,
            @Value("${jwt.absolute-token-expiry-days}") long absoluteExpiryDays
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtService = jwtService;
        this.refreshTokenExpiryDays = refreshTokenExpiryDays;
        this.absoluteExpiryDays = absoluteExpiryDays;
    }

    /**
     * Issues a new refresh token JWT and persists its hash.
     * Called on every fresh login (password or GitHub OAuth exchange) — never reuses
     * an existing row across separate login events (Section 7.3).
     * Returns the raw JWT — caller (AuthController) sets it as a cookie.
     */
    public String issueRefreshToken(UUID userId) {
        String rawJwt = jwtService.generateRefreshToken(userId);

        RefreshToken token = RefreshToken.builder()
                .userId(userId)
                .tokenHash(hash(rawJwt))
                .expiresAt(OffsetDateTime.now().plusDays(refreshTokenExpiryDays))
                .absoluteExpiresAt(OffsetDateTime.now().plusDays(absoluteExpiryDays))
                .build();

        refreshTokenRepository.save(token);
        return rawJwt;
    }

    /**
     * POST /auth/refresh (Section 7.2).
     * Full rotation flow with reuse detection:
     *
     * 1. Validate JWT signature/expiry/type — rejects cryptographically invalid tokens early.
     * 2. Hash and look up in DB.
     * 3. Not found → reject.
     * 4. Found, revoked_at set, replaced_by_token_id set → reuse detected, revoke entire chain.
     * 5. Found, revoked_at set, no replaced_by → already cleanly revoked (logout), reject.
     * 6. Absolute expiry exceeded → reject, force re-login regardless of rotation state.
     * 7. Valid → rotate: revoke old, issue new access + refresh tokens.
     *
     * Returns a RotationResult containing both new tokens — caller sets them as cookies.
     * Throws on any rejection path — AuthController clears cookies and forces re-login.
     */
    public RotationResult rotate(String rawRefreshJwt) {
        // Step 1 — JWT-level validation first (cheap, no DB hit on obviously bad tokens).
        UUID userId;
        try {
            userId = jwtService.validateRefreshToken(rawRefreshJwt);
        } catch (JwtException e) {
            throw new IllegalArgumentException("Invalid refresh token", e);
        }

        // Step 2 — DB lookup by hash.
        String tokenHash = hash(rawRefreshJwt);
        RefreshToken stored = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new IllegalArgumentException("Refresh token not recognised"));

        // Step 3+4 — Reuse detection (Section 7.2).
        if (stored.getRevokedAt() != null) {
            if (stored.getReplacedByTokenId() != null) {
                // This token was already rotated away and is being replayed — theft signal.
                // Revoke the entire chain for this user (OWASP-aligned, Section 7.2).
                revokeAllForUser(stored.getUserId());
                throw new IllegalStateException("Refresh token reuse detected — all sessions revoked");
            }
            // Step 5 — Cleanly revoked (logout path), no replacement exists.
            throw new IllegalArgumentException("Refresh token has been revoked");
        }

        // Step 6 — Absolute expiry check (session decision: 90-day hard ceiling).
        if (stored.getAbsoluteExpiresAt().isBefore(OffsetDateTime.now())) {
            // Revoke this token cleanly and force re-login — not a theft signal.
            stored.setRevokedAt(OffsetDateTime.now());
            refreshTokenRepository.save(stored);
            throw new IllegalStateException("Session expired — please log in again");
        }

        // Step 7 — Valid token, rotate.
        String newRefreshJwt = jwtService.generateRefreshToken(userId);
        RefreshToken newToken = RefreshToken.builder()
                .userId(userId)
                .tokenHash(hash(newRefreshJwt))
                .expiresAt(OffsetDateTime.now().plusDays(refreshTokenExpiryDays))
                .absoluteExpiresAt(stored.getAbsoluteExpiresAt()) // carry forward, never reset
                .build();

        RefreshToken savedNew = refreshTokenRepository.save(newToken);

        // Mark old token as rotated — replacedByTokenId is what triggers reuse detection
        // if this old token is ever seen again.
        stored.setRevokedAt(OffsetDateTime.now());
        stored.setReplacedByTokenId(savedNew.getId());
        refreshTokenRepository.save(stored);

        String newAccessJwt = jwtService.generateAccessToken(userId);
        return new RotationResult(newAccessJwt, newRefreshJwt);
    }

    /**
     * POST /auth/logout (Section 8).
     * Idempotent — returns normally whether or not a valid token was found.
     * Cookies are cleared by AuthController regardless of this method's outcome.
     */
    public void revokeSingle(String rawRefreshJwt) {
        if (rawRefreshJwt == null || rawRefreshJwt.isBlank()) {
            return; // no cookie present — already logged out, idempotent
        }

        // JWT-level validation — if it's garbage, there's nothing to revoke.
        UUID userId;
        try {
            userId = jwtService.validateRefreshToken(rawRefreshJwt);
        } catch (JwtException e) {
            return; // invalid/expired JWT — nothing to revoke, idempotent
        }

        String tokenHash = hash(rawRefreshJwt);
        refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(token -> {
            if (token.getRevokedAt() == null) {
                token.setRevokedAt(OffsetDateTime.now());
                // replacedByTokenId intentionally left null — revocation, not rotation.
                refreshTokenRepository.save(token);
            }
            // Already revoked — no-op, idempotent.
        });
    }

    /**
     * Revokes every active refresh token for a user.
     * Called on reuse detection (theft signal) and password reset (Section 9.4).
     */
    public void revokeAllForUser(UUID userId) {
        List<RefreshToken> active = refreshTokenRepository.findByUserIdAndRevokedAtIsNull(userId);
        OffsetDateTime now = OffsetDateTime.now();
        for (RefreshToken rt : active) {
            rt.setRevokedAt(now);
            // replacedByTokenId left null — bulk revocation, not rotation.
        }
        refreshTokenRepository.saveAll(active);
    }

    private static String hash(String rawJwt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawJwt.getBytes());
            return Base64.getEncoder().encodeToString(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * Carries both new tokens out of rotate() to AuthController.
     * Controller sets them as cookies — tokens never touch a response body.
     */
    public record RotationResult(String accessToken, String refreshToken) {}
}