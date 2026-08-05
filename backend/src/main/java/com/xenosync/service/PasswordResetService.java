package com.xenosync.service;

import com.xenosync.model.PasswordResetToken;
import com.xenosync.model.RefreshToken;
import com.xenosync.model.User;
import com.xenosync.repository.PasswordResetTokenRepository;
import com.xenosync.repository.RefreshTokenRepository;
import com.xenosync.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Transactional
@Service
public class PasswordResetService {

    private static final long TOKEN_TTL_HOURS = 1;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final PasswordResetTokenRepository tokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;

    public PasswordResetService(
            PasswordResetTokenRepository tokenRepository,
            RefreshTokenRepository refreshTokenRepository,
            UserRepository userRepository,
            EmailService emailService,
            PasswordEncoder passwordEncoder
    ) {
        this.tokenRepository = tokenRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * POST /auth/forgot-password (Section 9.1-9.2).
     * Always returns normally — caller (AuthController) sends the same generic response
     * regardless of whether the email exists or a token was actually issued.
     * Enumeration protection: no exception is thrown for unknown/GitHub-only accounts.
     */
    public void requestReset(String email) {
        Optional<User> maybeUser = userRepository.findByEmail(email);
        if (maybeUser.isEmpty()) {
            return; // generic response — do not reveal email doesn't exist
        }

        User user = maybeUser.get();

        // GitHub-only accounts have no password to reset (Section 9.5).
        // Same generic return — do not reveal account type.
        if (user.getPasswordHash() == null) {
            return;
        }

        invalidatePriorTokens(user.getId());

        String rawToken = generateRawToken();
        PasswordResetToken token = PasswordResetToken.builder()
                .userId(user.getId())
                .tokenHash(hash(rawToken))
                .expiresAt(OffsetDateTime.now().plusHours(TOKEN_TTL_HOURS))
                .build();

        tokenRepository.save(token);
        emailService.sendPasswordResetEmail(email, rawToken);
    }

    /**
     * POST /auth/reset-password (Section 9.3-9.4).
     * On success: updates password_hash, marks token used, revokes all refresh tokens
     * for that user (all active sessions logged out — standard response to a credential reset).
     * Throws on any invalid state — AuthController maps to a generic client-facing error.
     */
    public void consumeReset(String rawToken, String newPassword) {
        String tokenHash = hash(rawToken);

        PasswordResetToken token = tokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired token"));

        if (token.getUsedAt() != null) {
            throw new IllegalStateException("Token already used");
        }
        if (token.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new IllegalStateException("Token expired");
        }

        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new IllegalStateException("User no longer exists"));

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        token.setUsedAt(OffsetDateTime.now());

        userRepository.save(user);
        tokenRepository.save(token);

        // Revoke all active sessions — Section 9.4.
        // Likely follows a suspected compromise, so all sessions must be invalidated.
        revokeAllRefreshTokens(user.getId());
    }

    /**
     * Marks every active (non-revoked) refresh token for this user as revoked.
     * Called after a successful password reset.
     */
    private void revokeAllRefreshTokens(UUID userId) {
        List<RefreshToken> activeTokens = refreshTokenRepository.findByUserIdAndRevokedAtIsNull(userId);
        OffsetDateTime now = OffsetDateTime.now();
        for (RefreshToken rt : activeTokens) {
            rt.setRevokedAt(now);
            // replacedByTokenId intentionally left null — this is a revocation, not a rotation.
        }
        refreshTokenRepository.saveAll(activeTokens);
    }

    /**
     * Invalidates every unused reset token for a user before issuing a new one,
     * so only one valid reset token exists per user at a time (mirrors Section 3.2 pattern).
     */
    private void invalidatePriorTokens(UUID userId) {
        List<PasswordResetToken> priorTokens = tokenRepository.findByUserIdAndUsedAtIsNull(userId);
        OffsetDateTime now = OffsetDateTime.now();
        for (PasswordResetToken prior : priorTokens) {
            prior.setUsedAt(now);
        }
        tokenRepository.saveAll(priorTokens);
    }

    private static String generateRawToken() {
        byte[] bytes = new byte[32]; // 256-bit
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes());
            return Base64.getEncoder().encodeToString(hashed);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed available on every JVM — unreachable in practice.
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
