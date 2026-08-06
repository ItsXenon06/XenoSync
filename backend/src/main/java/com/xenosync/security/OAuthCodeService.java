package com.xenosync.security;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory, expiring code store for the GitHub OAuth flow (AUTH.md Section 5).
 * Two separate maps because the two payload types have genuinely different lifecycles —
 * see below. Splitting them avoids a single consume() trying to serve both semantics.
 *
 *   - loginCodes: a resolved userId, exchanged for real session cookies (Section 5, step 4).
 *     True single-use — burn on read. No retry concept applies to a login exchange.
 *
 *   - pendingCodes: a resolved GitHub identity that couldn't be auto-assigned a username
 *     due to a collision (SS2 decisions, Section 5 step 3b). Must survive a failed
 *     username attempt so the user can retry without restarting the whole GitHub OAuth
 *     flow — only invalidated once account creation actually succeeds.
 *
 * Deliberately not a DB table — AUTH.md Section 13: transient by nature, discarded once
 * consumed (or once it expires). Same reasoning as the original exchange-code design,
 * extended to cover the pending-signup case.
 *
 * Not cluster-safe — if XenoSync ever runs multiple backend instances this needs to move
 * to Redis or similar. Flagging now, not blocking on it.
 *
 * Also note: expired-but-never-consumed entries are never purged (only removed on a read
 * that finds them). A small memory leak over long uptime — fine for now, worth a
 * scheduled sweep later.
 *
 * pendingCodes retry surface is intentionally not attempt-limited here — repeated
 * peekPendingSignup() calls against the same code are bounded only by its TTL. Guarding
 * against abuse of that window is a rate-limiting concern (AUTH.md Section 10 already
 * lists /auth/oauth/complete-signup), not this store's job.
 */
@Service
public class OAuthCodeService {

    private static final long LOGIN_CODE_TTL_SECONDS = 60;
    private static final long PENDING_SIGNUP_TTL_SECONDS = 300; // 5 min — a human has to act

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final Map<String, LoginEntry> loginCodes = new ConcurrentHashMap<>();
    private final Map<String, PendingEntry> pendingCodes = new ConcurrentHashMap<>();

    public record LoginResult(UUID userId) {}

    public record PendingSignup(String githubId, String githubUsername, String email) {}

    private record LoginEntry(LoginResult payload, Instant expiresAt) {}

    private record PendingEntry(PendingSignup payload, Instant expiresAt) {}

    // ---- login codes: burn on read, single use always ----

    /** Issues a short-lived code for a resolved login (Section 5, step 4). */
    public String issueLoginCode(UUID userId) {
        String code = generateCode();
        loginCodes.put(code, new LoginEntry(new LoginResult(userId), Instant.now().plusSeconds(LOGIN_CODE_TTL_SECONDS)));
        return code;
    }

    /**
     * Consumes a login code — always single-use, regardless of outcome. Empty if the
     * code was never issued, already used, or expired.
     */
    public Optional<LoginResult> consumeLoginCode(String code) {
        LoginEntry entry = loginCodes.remove(code); // remove first: single-use even on an expired read
        if (entry == null || entry.expiresAt().isBefore(Instant.now())) {
            return Optional.empty();
        }
        return Optional.of(entry.payload());
    }

    // ---- pending signup codes: survive failed attempts, invalidated only on success ----

    /** Issues a longer-lived code for a username collision (SS2 decisions, step 3b). */
    public String issuePendingSignupCode(String githubId, String githubUsername, String email) {
        String code = generateCode();
        pendingCodes.put(code, new PendingEntry(
                new PendingSignup(githubId, githubUsername, email),
                Instant.now().plusSeconds(PENDING_SIGNUP_TTL_SECONDS)
        ));
        return code;
    }

    /**
     * Reads a pending-signup code without consuming it — safe to call on every
     * /auth/oauth/complete-signup attempt, including ones that fail because the
     * chosen username is also taken. Empty if the code was never issued, already
     * invalidated (prior success), or expired.
     */
    public Optional<PendingSignup> peekPendingSignup(String code) {
        PendingEntry entry = pendingCodes.get(code);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.expiresAt().isBefore(Instant.now())) {
            pendingCodes.remove(code);
            return Optional.empty();
        }
        return Optional.of(entry.payload());
    }

    /**
     * Invalidates a pending-signup code. Call only after the users row has actually
     * been created — this is what makes the code single-use overall, without punishing
     * a failed username attempt along the way.
     */
    public void invalidatePendingSignup(String code) {
        pendingCodes.remove(code);
    }

    private static String generateCode() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}