package com.xenosync.service;

import com.xenosync.model.EmailVerificationToken;
import com.xenosync.model.User;
import com.xenosync.repository.EmailVerificationTokenRepository;
import com.xenosync.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Transactional
@Service
public class EmailVerificationService {

    private static final String TYPE_VERIFY_EMAIL = "VERIFY_EMAIL";
    private static final String TYPE_ATTACH_PASSWORD = "ATTACH_PASSWORD";

    private static final long TOKEN_TTL_HOURS = 24;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final EmailVerificationTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;

    public EmailVerificationService(
            EmailVerificationTokenRepository tokenRepository,
            UserRepository userRepository,
            EmailService emailService
    ) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.emailService = emailService;
    }

    /**
     * Standard email verification flow (Section 3).
     * Invalidates any prior unused token for this user, issues a new one, emails it.
     */
    public void issueVerificationToken(UUID userId, String toEmail) {
        invalidatePriorTokens(userId);

        String rawToken = generateRawToken();
        EmailVerificationToken token = EmailVerificationToken.builder()
                .userId(userId)
                .tokenHash(hash(rawToken))
                .type(TYPE_VERIFY_EMAIL)
                .expiresAt(OffsetDateTime.now().plusHours(TOKEN_TTL_HOURS))
                .build();

        tokenRepository.save(token);
        emailService.sendVerificationEmail(toEmail, rawToken);
    }

    /**
     * Attach-password flow (Section 2, step 2c).
     * newPasswordHash must already be BCrypt-hashed by the caller (AuthService) —
     * this service never sees or handles a raw password.
     */
    public void issueAttachPasswordToken(UUID userId, String toEmail, String newPasswordHash) {
        invalidatePriorTokens(userId);

        String rawToken = generateRawToken();
        EmailVerificationToken token = EmailVerificationToken.builder()
                .userId(userId)
                .tokenHash(hash(rawToken))
                .type(TYPE_ATTACH_PASSWORD)
                .pendingPasswordHash(newPasswordHash)
                .expiresAt(OffsetDateTime.now().plusHours(TOKEN_TTL_HOURS))
                .build();

        tokenRepository.save(token);
        emailService.sendAttachPasswordEmail(toEmail, rawToken);
    }

    /**
     * POST /auth/verify-email — handles both VERIFY_EMAIL and ATTACH_PASSWORD token types,
     * since AUTH.md Section 3.5 specifies they share this mechanism.
     * Throws IllegalArgumentException/IllegalStateException on any invalid state —
     * caller (AuthController) maps these to a generic client-facing error.
     */
    public void consumeToken(String rawToken) {
        String tokenHash = hash(rawToken);

        EmailVerificationToken token = tokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired token"));

        if (token.getUsedAt() != null) {
            throw new IllegalStateException("Token already used");
        }
        if (token.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new IllegalStateException("Token expired");
        }

        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new IllegalStateException("User no longer exists"));

        switch (token.getType()) {
            case TYPE_VERIFY_EMAIL -> user.setEmailVerified(true);
            case TYPE_ATTACH_PASSWORD -> user.setPasswordHash(token.getPendingPasswordHash());
            default -> throw new IllegalStateException("Unknown token type: " + token.getType());
        }

        token.setUsedAt(OffsetDateTime.now());
        userRepository.save(user);
        tokenRepository.save(token);
    }

    /**
     * Invalidates every unused token for a user before issuing a new one,
     * so only one valid token exists per user at a time (Section 3.2).
     */
    private void invalidatePriorTokens(UUID userId) {
        List<EmailVerificationToken> priorTokens = tokenRepository.findByUserIdAndUsedAtIsNull(userId);
        OffsetDateTime now = OffsetDateTime.now();
        for (EmailVerificationToken prior : priorTokens) {
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
            // SHA-256 is guaranteed available on every JVM — this is unreachable in practice.
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}