package com.xenosync.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

/**
 * AUTH.md Section 10 — rate limiting on the endpoints named there, plus
 * /auth/oauth/complete-signup (SS2 decisions: same abuse shape as /auth/oauth/exchange,
 * a guessable single-use code). Combines a per-IP bucket and a per-account-equivalent
 * bucket per request — both must have capacity, matching "per-IP and per-account keys,
 * combined" rather than either alone.
 *
 * "Account key" is the request body's email for endpoints that have one. For
 * reset-password, oauth/exchange, and oauth/complete-signup — which have no email in
 * their body — the token/code itself is used as the account-equivalent key, since it's
 * already the unique high-entropy value an attacker would be guessing against.
 *
 * IP is read from request.getRemoteAddr() — does NOT account for a reverse proxy
 * (X-Forwarded-For). Fine for now since nothing in this codebase configures one yet;
 * flagging so it isn't forgotten if XenoSync ends up behind Nginx/a load balancer,
 * per the containerization already referenced in session_containers.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private record Policy(String pathSuffix, String accountField, Bandwidth ipLimit, Bandwidth accountLimit) {}
    private static final int HTTP_STATUS_TOO_MANY_REQUESTS = 429;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Policy[] POLICIES = new Policy[] {
            new Policy("/auth/login", "email",
                    limit(10, Duration.ofMinutes(5)), limit(5, Duration.ofMinutes(5))),
            new Policy("/auth/register", "email",
                    limit(10, Duration.ofHours(1)), limit(3, Duration.ofHours(1))),
            new Policy("/auth/forgot-password", "email",
                    limit(10, Duration.ofHours(1)), limit(3, Duration.ofHours(1))),
            new Policy("/auth/resend-verification", "email",
                    limit(10, Duration.ofHours(1)), limit(3, Duration.ofHours(1))),
            new Policy("/auth/reset-password", "token",
                    limit(20, Duration.ofMinutes(15)), limit(5, Duration.ofMinutes(15))),
            new Policy("/auth/oauth/exchange", "code",
                    limit(30, Duration.ofMinutes(1)), limit(10, Duration.ofMinutes(1))),
            new Policy("/auth/oauth/complete-signup", "code",
                    limit(30, Duration.ofMinutes(1)), limit(10, Duration.ofMinutes(1))),
    };

    private final RateLimitService rateLimitService;

    public RateLimitFilter(RateLimitService rateLimitService) {
        this.rateLimitService = rateLimitService;
    }

    private static Bandwidth limit(int capacity, Duration period) {
        return Bandwidth.classic(capacity, Refill.greedy(capacity, period));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        Policy policy = matchPolicy(request);
        if (policy == null) {
            filterChain.doFilter(request, response);
            return;
        }

        CachedBodyHttpServletRequest cachedRequest = new CachedBodyHttpServletRequest(request);

        String ip = request.getRemoteAddr();
        String ipKey = policy.pathSuffix() + ":ip:" + ip;
        if (!rateLimitService.tryConsume(ipKey, policy.ipLimit())) {
            reject(response);
            return;
        }

        String accountValue = extractField(cachedRequest, policy.accountField());
        if (accountValue != null && !accountValue.isBlank()) {
            String accountKey = policy.pathSuffix() + ":acct:" + accountValue;
            if (!rateLimitService.tryConsume(accountKey, policy.accountLimit())) {
                reject(response);
                return;
            }
        }
        // Missing/unparseable field: fall through on IP limiting alone rather than
        // blocking the request outright — a malformed body will fail validation
        // downstream anyway, and that's @Valid's job, not this filter's.

        filterChain.doFilter(cachedRequest, response);
    }

    private Policy matchPolicy(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return null;
        }
        String path = request.getRequestURI();
        for (Policy policy : POLICIES) {
            if (path.equals(policy.pathSuffix())) {
                return policy;
            }
        }
        return null;
    }

    private String extractField(CachedBodyHttpServletRequest cachedRequest, String fieldName) {
        try {
            JsonNode node = MAPPER.readTree(cachedRequest.getCachedBodyAsString());
            JsonNode field = node.get(fieldName);
            return field == null ? null : field.asText(null);
        } catch (Exception e) {
            return null; // malformed JSON — let downstream @Valid handle the rejection
        }
    }

    private void reject(HttpServletResponse response) throws IOException {
        response.setStatus(HTTP_STATUS_TOO_MANY_REQUESTS);
        response.setContentType("application/json");
        response.getWriter().write(
                MAPPER.writeValueAsString(Map.of("message", "Too many requests — please try again later."))
        );
    }
}