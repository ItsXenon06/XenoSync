package com.xenosync.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Handles any failure in the GitHub OAuth2 login pipeline (AUTH.md Section 5),
 * including CustomOAuth2UserService's verified-email resolution failures.
 *
 * Deliberately separates internal error detail from what the frontend sees:
 *   - Full exception (code, message, cause) is logged server-side for debugging —
 *     this is the only place that information exists at all, since
 *     OAuth2AuthenticationException never reaches the frontend directly.
 *   - Only a small, fixed set of user-facing codes reaches the redirect URL.
 *     "no_verified_email" is the one case worth telling the user about specifically
 *     (it's actionable — go verify an email on GitHub). Every other OAuth-related
 *     failure (github_email_fetch_failed, GitHub being down, state mismatch, etc.)
 *     collapses into a generic "oauth_failed" — none of those are the user's fault
 *     or something they can act on, so there's nothing to gain by exposing the
 *     specific internal code and it would just leak backend detail for no benefit.
 */
@Component
public class GithubOAuth2FailureHandler implements AuthenticationFailureHandler {

    private static final Logger log = LoggerFactory.getLogger(GithubOAuth2FailureHandler.class);

    private static final String CODE_NO_VERIFIED_EMAIL = "no_verified_email";
    private static final String USER_FACING_GENERIC = "oauth_failed";

    private final String appBaseUrl;

    public GithubOAuth2FailureHandler(@Value("${app.base-url}") String appBaseUrl) {
        this.appBaseUrl = appBaseUrl;
    }

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException, ServletException {

        String internalCode = extractInternalCode(exception);
        log.warn("GitHub OAuth2 login failed [{}]: {}", internalCode, exception.getMessage(), exception);

        String userFacingCode = CODE_NO_VERIFIED_EMAIL.equals(internalCode)
                ? CODE_NO_VERIFIED_EMAIL
                : USER_FACING_GENERIC;

        response.sendRedirect(appBaseUrl + "/login?error=" + userFacingCode);
    }

    private static String extractInternalCode(AuthenticationException exception) {
        if (exception instanceof OAuth2AuthenticationException oauthEx && oauthEx.getError() != null) {
            return oauthEx.getError().getErrorCode();
        }
        return "unknown";
    }
}