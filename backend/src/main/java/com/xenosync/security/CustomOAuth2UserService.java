package com.xenosync.security;

import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Wraps the default GitHub OAuth2 user service to additionally resolve a verified email
 * (AUTH.md Section 5, amended per SS2 decisions).
 *
 * GitHub's default /user endpoint (what DefaultOAuth2UserService calls) returns only the
 * public primary email — often null — with no verified-status field at all. Verified
 * status only comes from a separate authenticated call to /user/emails. This class makes
 * that call and folds a single resolved email into the OAuth2User's attributes, so
 * GithubOAuth2SuccessHandler never touches the GitHub API itself and only ever sees
 * already-verified, already-normalized data.
 *
 * Resolution order (confirmed this session):
 *   1. Primary email, if verified.
 *   2. Any other verified email, if primary isn't verified.
 *   3. None verified -> throw OAuth2AuthenticationException("no_verified_email"),
 *      routed by GithubOAuth2FailureHandler to a ?error=no_verified_email redirect.
 *
 * Only the single resolved email is retained in attributes — the full /user/emails
 * response (every address on the account, verified or not) is discarded once resolution
 * is done. Mirrors the minimal-claims reasoning AUTH.md Section 6 already applies to JWTs.
 */
@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    public static final String ATTR_RESOLVED_EMAIL = "xenosync_resolved_email";

    private static final String GITHUB_EMAILS_URL = "https://api.github.com/user/emails";

    private final RestClient restClient = RestClient.create();

    private record GithubEmail(String email, boolean primary, boolean verified) {}

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User delegateUser = super.loadUser(userRequest);

        String accessToken = userRequest.getAccessToken().getTokenValue();
        List<GithubEmail> emails = fetchEmails(accessToken);

        String resolvedEmail = resolveVerifiedEmail(emails);
        if (resolvedEmail == null) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("no_verified_email"),
                    "No verified email found on GitHub account"
            );
        }

        Map<String, Object> attributes = new LinkedHashMap<>(delegateUser.getAttributes());
        attributes.put(ATTR_RESOLVED_EMAIL, resolvedEmail);

        return new DefaultOAuth2User(delegateUser.getAuthorities(), attributes, "id");
    }

    /**
     * Fetches the account's email list. A fetch failure (GitHub unreachable, rate-limited,
     * token missing user:email scope, etc.) is deliberately NOT collapsed into
     * "no verified email" — it's surfaced as its own error code so an infra problem
     * doesn't get presented to the user as an account problem.
     */
    private List<GithubEmail> fetchEmails(String accessToken) {
        try {
            GithubEmail[] emails = restClient.get()
                    .uri(GITHUB_EMAILS_URL)
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Accept", "application/vnd.github+json")
                    .retrieve()
                    .body(GithubEmail[].class);

            return emails == null ? List.of() : List.of(emails);
        } catch (Exception e) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("github_email_fetch_failed"),
                    "Failed to fetch verified emails from GitHub",
                    e
            );
        }
    }

    private static String resolveVerifiedEmail(List<GithubEmail> emails) {
        String fallback = null;
        for (GithubEmail email : emails) {
            if (email.verified() && email.primary()) {
                return email.email();
            }
            if (email.verified() && fallback == null) {
                fallback = email.email();
            }
        }
        return fallback;
    }
}