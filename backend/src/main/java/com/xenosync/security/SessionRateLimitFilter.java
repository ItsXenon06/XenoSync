package com.xenosync.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * Rate limits session endpoints by authenticated userId (from JwtAuthFilter's
 * principal), not IP — session creation/join/leave are all gated behind auth,
 * so userId is the correct, unspoofable key (unlike the pre-auth /auth/*
 * endpoints RateLimitFilter handles by body field).
 * Must run AFTER JwtAuthFilter — the principal must already be populated.
 */
@Component
public class SessionRateLimitFilter extends OncePerRequestFilter {

    private record Policy(String pathSuffix, String method, Bandwidth limit) {}

    private static final Policy[] POLICIES = new Policy[] {
            new Policy("/v1/session/create", "POST",
                    Bandwidth.classic(10, Refill.greedy(10, Duration.ofHours(1)))),
            new Policy("/v1/session/join/", "POST",
                    Bandwidth.classic(20, Refill.greedy(20, Duration.ofMinutes(5)))),
    };

    private final RateLimitService rateLimitService;

    public SessionRateLimitFilter(RateLimitService rateLimitService) {
        this.rateLimitService = rateLimitService;
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

        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof UUID userId)) {
            // No valid principal — let the request through; authorizeHttpRequests
            // will reject it as unauthenticated. Not this filter's job to 401.
            filterChain.doFilter(request, response);
            return;
        }

        String key = policy.pathSuffix() + ":user:" + userId;
        if (!rateLimitService.tryConsume(key, policy.limit())) {
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"message\":\"Too many requests — please try again later.\"}"
            );
            return;
        }

        filterChain.doFilter(request, response);
    }

    private Policy matchPolicy(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return null;
        }
        String path = request.getServletPath();
        for (Policy policy : POLICIES) {
            if (path.startsWith(policy.pathSuffix())) {
                return policy;
            }
        }
        return null;
    }
}