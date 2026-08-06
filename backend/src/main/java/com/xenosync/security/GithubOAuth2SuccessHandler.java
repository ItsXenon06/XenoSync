package com.xenosync.security;

import com.xenosync.model.User;
import com.xenosync.repository.UserRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Resolves a successful GitHub OAuth2 login (AUTH.md Section 5, step 3, amended by
 * SS2 decisions) into one of three outcomes. Runs only after CustomOAuth2UserService
 * has already attached a single verified email under ATTR_RESOLVED_EMAIL — this class
 * never touches the GitHub API itself.
 *
 * Resolution order (Section 5, step 3):
 *   1. github_id already on a user -> returning GitHub user, log in directly.
 *   2. No github_id match, but resolved email matches an existing user's email ->
 *      link: set github_id + github_username on that row. If that account was
 *      Unverified, it becomes Verified + GitHub immediately (GitHub's verification
 *      stands in for ours).
 *   3. No match at all -> brand-new account, split per SS2 decisions:
 *      3a. github_username free (case-insensitive) -> create user immediately,
 *          issue a login code.
 *      3b. github_username taken -> don't create a user row yet; issue a
 *          pending-signup code and redirect to a username-choice step.
 *
 * All three success paths funnel to the same *shape* of redirect (a placeholder
 * frontend URL carrying a single-use code) — only the target path and code type
 * differ, so the frontend can tell "you're logged in, go exchange this" apart from
 * "pick a username first."
 */
@Component
public class GithubOAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final OAuthCodeService oAuthCodeService;
    private final String appBaseUrl;

    public GithubOAuth2SuccessHandler(
            UserRepository userRepository,
            OAuthCodeService oAuthCodeService,
            @Value("${app.base-url}") String appBaseUrl
    ) {
        this.userRepository = userRepository;
        this.oAuthCodeService = oAuthCodeService;
        this.appBaseUrl = appBaseUrl;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {

        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();

        String githubId = String.valueOf(oAuth2User.getAttributes().get("id"));
        String githubUsername = (String) oAuth2User.getAttributes().get("login");
        String resolvedEmail = (String) oAuth2User.getAttributes().get(CustomOAuth2UserService.ATTR_RESOLVED_EMAIL);

        // Step 1 — returning GitHub user.
        var byGithubId = userRepository.findByGithubId(githubId);
        if (byGithubId.isPresent()) {
            redirectToLogin(response, byGithubId.get().getId());
            return;
        }

        // Step 2 — link to an existing email-matched account.
        var byEmail = userRepository.findByEmail(resolvedEmail);
        if (byEmail.isPresent()) {
            User existing = byEmail.get();
            existing.setGithubId(githubId);
            existing.setGithubUsername(githubUsername);
            existing.setEmailVerified(true); // GitHub's verification stands in for ours
            userRepository.save(existing);
            redirectToLogin(response, existing.getId());
            return;
        }

        // Step 3 — brand-new account. Split on username collision (SS2 decisions).
        if (userRepository.existsByUsernameIgnoreCase(githubUsername)) {
            // 3b — collision: don't create the user yet, hold identity for username choice.
            String pendingCode = oAuthCodeService.issuePendingSignupCode(githubId, githubUsername, resolvedEmail);
            response.sendRedirect(appBaseUrl + "/oauth/choose-username?code=" + pendingCode);
            return;
        }

// 3a — free at check time: attempt immediate creation.
        User newUser = User.builder()
                .username(githubUsername)
                .email(resolvedEmail)
                .passwordHash(null)
                .githubId(githubId)
                .githubUsername(githubUsername)
                .emailVerified(true)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();

        try {
            User saved = userRepository.save(newUser);
            redirectToLogin(response, saved.getId());
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // Lost the race after the pre-check. Two distinct causes, two distinct responses:
            //
            //   - github_id collision: this exact GitHub account raced itself (double-click,
            //     two tabs) — both requests missed step 1's findByGithubId before either
            //     committed. Not a username problem; re-resolve as a normal returning-user
            //     login rather than sending them to pick a username for no reason.
            //
            //   - username collision: someone else claimed this username in the gap between
            //     our check and this insert. Genuinely the same situation as if
            //     existsByUsernameIgnoreCase had caught it a moment earlier — fall back to 3b.
            var raceWinner = userRepository.findByGithubId(githubId);
            if (raceWinner.isPresent()) {
                redirectToLogin(response, raceWinner.get().getId());
                return;
            }
            String pendingCode = oAuthCodeService.issuePendingSignupCode(githubId, githubUsername, resolvedEmail);
            response.sendRedirect(appBaseUrl + "/oauth/choose-username?code=" + pendingCode);
        }
    }

    private void redirectToLogin(HttpServletResponse response, UUID userId) throws IOException {
        String code = oAuthCodeService.issueLoginCode(userId);
        response.sendRedirect(appBaseUrl + "/oauth/callback?code=" + code);
    }
}