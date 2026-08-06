package com.xenosync.service;

import com.xenosync.dto.AuthResponse;
import com.xenosync.model.User;
import com.xenosync.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Transactional
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationService emailVerificationService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            EmailVerificationService emailVerificationService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailVerificationService = emailVerificationService;
    }

    /**
     * POST /auth/register (Section 2).
     * Three outcomes, all return the same generic response to the caller —
     * AuthController always responds with "check your email" regardless of which path ran.
     *
     * 1. Username already taken (regardless of email state) → throw, controller maps to generic error.
     * 2. Email belongs to a GitHub-only account (password_hash IS NULL) → issue ATTACH_PASSWORD token.
     * 3. Email already has a password account → throw, controller maps to same generic error as (1).
     * 4. No existing user → normal registration path.
     *
     * Enumeration note: (1) and (3) throw the same exception type with the same message,
     * so AuthController can return one generic "unable to register" response for both.
     * This does not fully close the enumeration gap (AUTH.md Section 13) but is the
     * accepted industry tradeoff.
     */
    public void register(String username, String email, String password, String displayName) {
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new IllegalArgumentException("Registration failed");
        }

        userRepository.findByEmail(email).ifPresentOrElse(existingUser -> {
            if (existingUser.getPasswordHash() == null) {
                // GitHub-only account — stage the password, send confirmation email.
                String pendingHash = passwordEncoder.encode(password);
                emailVerificationService.issueAttachPasswordToken(
                        existingUser.getId(), email, pendingHash
                );
            } else {
                // Email already has a password account — same generic error as username conflict.
                throw new IllegalArgumentException("Registration failed");
            }
        }, () -> {
            // No existing user — normal registration path.
            User user = User.builder()
                    .username(username)
                    .email(email)
                    .passwordHash(passwordEncoder.encode(password))
                    .displayName(displayName)
                    .emailVerified(false)
                    .build();

            try {
                User saved = userRepository.save(user);
                emailVerificationService.issueVerificationToken(saved.getId(), email);
            } catch (org.springframework.dao.DataIntegrityViolationException e) {
                // Lost the race — another request claimed this username/email between our
                // pre-check and this insert. Same generic response as the pre-check losses,
                // preserving enumeration protection either way.
                throw new IllegalArgumentException("Registration failed");
            }
        });
    }

    /**
     * POST /auth/login (Section 4).
     * Returns the User on success — RefreshTokenService and AuthController
     * handle token issuance and cookie setting respectively.
     *
     * All failure cases (no account, GitHub-only, wrong password) throw the same
     * exception with the same message — enumeration protection.
     */
    public User login(String email, String password) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));

        // GitHub-only accounts have no password — treated identically to wrong password.
        if (user.getPasswordHash() == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid email or password");
        }

        // email_verified is NOT checked here — unverified users can log in,
        // they are restricted at action level (Section 1, Section 11).
        return user;
    }

    /**
     * Guards any action that requires a verified email (Section 11).
     * Called explicitly at each restricted entry point — not a filter.
     * Live DB check every time, never trusts a cached or JWT claim.
     */
    public void requireVerifiedEmail(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("User not found"));

        if (!user.isEmailVerified()) {
            throw new IllegalStateException("Email verification required");
        }
    }

    /**
     * Builds the AuthResponse DTO from a User.
     * Used by AuthController after login and OAuth exchange — keeps DTO assembly
     * out of the controller and out of RefreshTokenService.
     */
    public AuthResponse toAuthResponse(User user) {
        return new AuthResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.isEmailVerified(),
                user.getDisplayName()
        );
    }
}