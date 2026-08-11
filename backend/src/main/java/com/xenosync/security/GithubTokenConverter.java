package com.xenosync.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-GCM converter for session_repositories.github_access_token.
 *
 * Format on the wire (DB column, Base64-encoded): IV (12 bytes) || ciphertext+tag.
 * Key comes from GITHUB_TOKEN_ENC_KEY (env), separate from JWT_SECRET — a leak of
 * one must not compromise the other.
 *
 * NOT autoApply — deliberately applied only via @Convert on SessionLinkedRepo.githubAccessToken,
 * so no other String column in the codebase silently picks this up.
 *
 * Loaded via System.getenv directly rather than @Value: AttributeConverter instances
 * are constructed by the JPA provider, not Spring, so constructor injection isn't reliable here.
 */
@Converter
public class GithubTokenConverter implements AttributeConverter<String, String> {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final SecretKeySpec SECRET_KEY = loadKey();

    @Override
    public String convertToDatabaseColumn(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            SECURE_RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, SECRET_KEY, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to encrypt github_access_token", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String stored) {
        if (stored == null) {
            return null;
        }
        try {
            byte[] combined = Base64.getDecoder().decode(stored);
            if (combined.length < GCM_IV_LENGTH_BYTES) {
                throw new IllegalStateException("Stored github_access_token is malformed (too short)");
            }

            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH_BYTES);

            byte[] ciphertext = new byte[combined.length - GCM_IV_LENGTH_BYTES];
            System.arraycopy(combined, GCM_IV_LENGTH_BYTES, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, SECRET_KEY, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));

            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            // Covers AEADBadTagException (wrong key / tampered data) among others.
            throw new IllegalStateException("Failed to decrypt github_access_token", e);
        }
    }

    private static SecretKeySpec loadKey() {
        String encoded = System.getenv("GITHUB_TOKEN_ENC_KEY");
        if (encoded == null || encoded.isBlank()) {
            throw new IllegalStateException("GITHUB_TOKEN_ENC_KEY environment variable is not set");
        }
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("GITHUB_TOKEN_ENC_KEY is not valid Base64", e);
        }
        if (keyBytes.length != 32) {
            throw new IllegalStateException(
                    "GITHUB_TOKEN_ENC_KEY must decode to 32 bytes (AES-256), got " + keyBytes.length);
        }
        return new SecretKeySpec(keyBytes, "AES");
    }
}