package com.xenosync.controller;

import com.xenosync.dto.*;
import com.xenosync.model.User;
import com.xenosync.repository.UserRepository;
import com.xenosync.security.JwtService;
import com.xenosync.security.OAuthCodeService;
import com.xenosync.service.AuthService;
import com.xenosync.service.EmailVerificationService;
import com.xenosync.service.PasswordResetService;
import com.xenosync.service.RefreshTokenService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * AUTH.md sections 2, 3, 4, 5, 8, 9 — assembled against services that already own
 * their individual pieces of logic. This controller's job is orchestration + cookie
 * handling only; it deliberately doesn't re-implement any decision already made in
 * AuthService / RefreshTokenService / EmailVerificationService / PasswordResetService /
 * OAuthCodeService.
 *
 * No @ControllerAdvice exists yet — every endpoint catches its own service exceptions
 * inline and maps them to the generic, enumeration-safe messages AUTH.md specifies.
 * Worth revisiting as a shared exception handler once the exception types across
 * services are more consistent (currently a mix of IllegalArgumentException /
 * IllegalStateException / RuntimeException without a shared hierarchy).
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final String ACCESS_COOKIE_NAME = "access_token";
    private static final String REFRESH_COOKIE_NAME = "refresh_token";
    private static final String REFRESH_COOKIE_PATH = "/auth/refresh";

    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;
    private final EmailVerificationService emailVerificationService;
    private final PasswordResetService passwordResetService;
    private final OAuthCodeService oAuthCodeService;
    private final UserRepository userRepository;
    private final JwtService jwtService;

    private final long accessTokenExpiryMinutes;
    private final long refreshTokenExpiryDays;

    public AuthController(
            AuthService authService,
            RefreshTokenService refreshTokenService,
            EmailVerificationService emailVerificationService,
            PasswordResetService passwordResetService,
            OAuthCodeService oAuthCodeService,
            UserRepository userRepository,
            JwtService jwtService,
            @Value("${jwt.access-token-expiry-minutes}") long accessTokenExpiryMinutes,
            @Value("${jwt.refresh-token-expiry-days}") long refreshTokenExpiryDays
    ) {
        this.authService = authService;
        this.refreshTokenService = refreshTokenService;
        this.emailVerificationService = emailVerificationService;
        this.passwordResetService = passwordResetService;
        this.oAuthCodeService = oAuthCodeService;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.accessTokenExpiryMinutes = accessTokenExpiryMinutes;
        this.refreshTokenExpiryDays = refreshTokenExpiryDays;
    }

    // ---- Section 2: Registration ----

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest req) {
        try {
            authService.register(req.username(), req.email(), req.password(), req.displayName());
        } catch (IllegalArgumentException e) {
            // Deliberately still 200 + generic body, not 400 — AUTH.md Section 2/13:
            // register's response must not distinguish failure reasons from success
            // at the transport level either, or the enumeration protection leaks
            // through status codes even with identical bodies.
        }
        return ResponseEntity.ok(Map.of("message", "Check your email to verify your account."));
    }

    // ---- Section 4: Login ----

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req) {
        User user;
        try {
            user = authService.login(req.email(), req.password());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Invalid email or password"));
        }
        return issueSession(user);
    }

    // ---- Section 7: Refresh ----

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(HttpServletRequest request) {
        String rawRefresh = extractCookie(request, REFRESH_COOKIE_NAME);
        if (rawRefresh == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Not authenticated"));
        }

        RefreshTokenService.RotationResult result;
        try {
            result = refreshTokenService.rotate(rawRefresh);
        } catch (IllegalArgumentException | IllegalStateException e) {
            // Covers: not found, cleanly revoked, reuse-detected, absolute expiry —
            // all force re-login per AUTH.md Section 7.2. Clear cookies either way
            // so the client doesn't keep sending a dead refresh cookie.
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .header(HttpHeaders.SET_COOKIE, clearCookie(ACCESS_COOKIE_NAME, "/").toString())
                    .header(HttpHeaders.SET_COOKIE, clearCookie(REFRESH_COOKIE_NAME, REFRESH_COOKIE_PATH).toString())
                    .body(Map.of("message", "Session expired — please log in again"));
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, accessCookie(result.accessToken()).toString())
                .header(HttpHeaders.SET_COOKIE, refreshCookie(result.refreshToken()).toString())
                .build();
    }

    // ---- Section 5: GitHub OAuth exchange ----

    @PostMapping("/oauth/exchange")
    public ResponseEntity<?> oauthExchange(@Valid @RequestBody OAuthExchangeRequest req) {
        var payload = oAuthCodeService.consumeLoginCode(req.code());
        if (payload.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Invalid or expired code"));
        }

        User user = userRepository.findById(payload.get().userId())
                .orElse(null);
        if (user == null) {
            // Resolved userId no longer exists (deleted between code issuance and
            // exchange) — edge case, but the code path shouldn't NPE on it.
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Invalid or expired code"));
        }

        return issueSession(user);
    }

    // ---- SS2 decisions: username-collision completion (Section 5 step 3b) ----

    @PostMapping("/oauth/complete-signup")
    public ResponseEntity<?> completeSignup(@Valid @RequestBody CompleteSignupRequest req) {
        var pending = oAuthCodeService.peekPendingSignup(req.code());
        if (pending.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Invalid or expired code"));
        }

        if (userRepository.existsByUsernameIgnoreCase(req.username())) {
            // Code stays alive — this is the retry case the split-lifecycle
            // OAuthCodeService design exists for.
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "Username already taken"));
        }

        var identity = pending.get();
        User newUser = User.builder()
                .username(req.username())
                .email(identity.email())
                .passwordHash(null)
                .githubId(identity.githubId())
                .githubUsername(identity.githubUsername())
                .emailVerified(true)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();

        User saved;
        try {
            saved = userRepository.save(newUser);
        } catch (DataIntegrityViolationException e) {
            // Lost the race after the pre-check (SS2 decisions: TOCTOU review).
            // Code stays alive — same as the taken-username response above, just
            // discovered a moment later than the check caught it.
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "Username already taken"));
        }

        oAuthCodeService.invalidatePendingSignup(req.code());
        return issueSession(saved);
    }

    // ---- Section 3: Email verification ----

    @PostMapping("/verify-email")
    public ResponseEntity<?> verifyEmail(@Valid @RequestBody VerifyEmailRequest req) {
        try {
            emailVerificationService.consumeToken(req.token());
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Invalid or expired token"));
        }
        return ResponseEntity.ok(Map.of("message", "Email verified"));
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<?> resendVerification(@Valid @RequestBody ResendVerificationRequest req) {
        userRepository.findByEmail(req.email()).ifPresent(user -> {
            if (!user.isEmailVerified()) {
                emailVerificationService.issueVerificationToken(user.getId(), req.email());
            }
            // Already verified, or GitHub-only (email_verified already true) — no-op.
            // Same generic response either way, no distinction leaked.
        });
        return ResponseEntity.ok(Map.of("message", "If an account exists, a verification email was sent."));
    }

    // ---- Section 9: Forgot / reset password ----

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@Valid @RequestBody ForgotPasswordRequest req) {
        passwordResetService.requestReset(req.email());
        return ResponseEntity.ok(Map.of("message", "If an account exists, a reset email was sent."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        try {
            passwordResetService.consumeReset(req.token(), req.newPassword());
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Invalid or expired token"));
        }
        // All refresh tokens for this user were just revoked server-side (Section 9.4) —
        // clear this request's own cookies too, since they're now dead regardless of
        // whether this request happened to be the account owner's own browser.
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, clearCookie(ACCESS_COOKIE_NAME, "/").toString())
                .header(HttpHeaders.SET_COOKIE, clearCookie(REFRESH_COOKIE_NAME, REFRESH_COOKIE_PATH).toString())
                .body(Map.of("message", "Password reset. Please log in again."));
    }

    // ---- Section 8: Logout ----

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request) {
        String rawRefresh = extractCookie(request, REFRESH_COOKIE_NAME);
        refreshTokenService.revokeSingle(rawRefresh); // idempotent, handles null internally
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, clearCookie(ACCESS_COOKIE_NAME, "/").toString())
                .header(HttpHeaders.SET_COOKIE, clearCookie(REFRESH_COOKIE_NAME, REFRESH_COOKIE_PATH).toString())
                .body(Map.of("message", "Logged out"));
    }

    // ---- shared helpers ----

    private ResponseEntity<?> issueSession(User user) {
        String accessJwt = jwtService.generateAccessToken(user.getId());
        String refreshJwt = refreshTokenService.issueRefreshToken(user.getId());

        AuthResponse body = authService.toAuthResponse(user);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, accessCookie(accessJwt).toString())
                .header(HttpHeaders.SET_COOKIE, refreshCookie(refreshJwt).toString())
                .body(body);
    }

    private ResponseCookie accessCookie(String value) {
        return ResponseCookie.from(ACCESS_COOKIE_NAME, value)
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ofMinutes(accessTokenExpiryMinutes))
                .build();
    }

    private ResponseCookie refreshCookie(String value) {
        return ResponseCookie.from(REFRESH_COOKIE_NAME, value)
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path(REFRESH_COOKIE_PATH)
                .maxAge(Duration.ofDays(refreshTokenExpiryDays))
                .build();
    }

    private ResponseCookie clearCookie(String name, String path) {
        return ResponseCookie.from(name, "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path(path)
                .maxAge(Duration.ZERO)
                .build();
    }

    private String extractCookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}