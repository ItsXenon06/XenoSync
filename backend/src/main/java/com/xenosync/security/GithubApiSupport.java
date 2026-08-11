package com.xenosync.security;

import com.xenosync.exception.GithubApiException;
import org.springframework.web.client.RestClientResponseException;

import java.util.function.Supplier;

/**
 * Shared wrapper so GithubAppAuthService, GithubRepoClient, and GithubArchiveClient all turn
 * a failed GitHub call into the same GithubApiException -> 502 GITHUB_API_ERROR response,
 * instead of each falling through GlobalExceptionHandler's generic 500 catch-all differently.
 */
public final class GithubApiSupport {

    private GithubApiSupport() {}

    public static <T> T call(String description, Supplier<T> githubCall) {
        try {
            return githubCall.get();
        } catch (RestClientResponseException e) {
            throw new GithubApiException(
                    description + " failed (" + e.getStatusCode() + "): " + e.getStatusText(), e);
        } catch (IllegalStateException e) {
            // Our own clients already throw IllegalStateException for null/malformed GitHub
            // responses (empty body, bad encoding, etc.) — normalize those too rather than
            // letting them fall through as unrelated 500s.
            throw new GithubApiException(description + " failed: " + e.getMessage(), e);
        }
    }
}