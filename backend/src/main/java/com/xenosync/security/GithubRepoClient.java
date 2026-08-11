package com.xenosync.security;

import com.xenosync.exception.ConflictException;
import com.xenosync.exception.GithubApiException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Thin GitHub REST wrapper for repo metadata + branch creation, authenticated with an
 * installation access token (from GithubAppAuthService — never an OAuth user token).
 * Scoped deliberately narrow: only what RepoLinkingService needs right now. Tree/blob/archive
 * fetching for file import lives in GithubArchiveClient, not here.
 */
@Service
public class GithubRepoClient {

    private static final String GITHUB_API_BASE = "https://api.github.com";
    private final RestClient restClient = RestClient.create();

    public record RepoMetadata(int sizeKb, String defaultBranch) {}

    public RepoMetadata fetchRepoMetadata(String token, String owner, String repo) {
        return GithubApiSupport.call("Fetch repo metadata for " + owner + "/" + repo, () -> {
            Map<String, Object> response = restClient.get()
                    .uri(GITHUB_API_BASE + "/repos/{owner}/{repo}", owner, repo)
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/vnd.github+json")
                    .retrieve()
                    .body(Map.class);

            if (response == null) {
                throw new IllegalStateException("GitHub returned no repo metadata for " + owner + "/" + repo);
            }
            int sizeKb = ((Number) response.getOrDefault("size", 0)).intValue();
            String defaultBranch = (String) response.get("default_branch");
            return new RepoMetadata(sizeKb, defaultBranch);
        });
    }

    /** Resolves a branch name to the SHA of its tip commit — needed to create a new branch from it. */
    public String resolveBranchSha(String token, String owner, String repo, String branch) {
        return GithubApiSupport.call("Resolve branch " + branch + " for " + owner + "/" + repo, () -> {
            Map<String, Object> response = restClient.get()
                    .uri(GITHUB_API_BASE + "/repos/{owner}/{repo}/git/ref/heads/{branch}", owner, repo, branch)
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/vnd.github+json")
                    .retrieve()
                    .body(Map.class);

            if (response == null) {
                throw new IllegalStateException(
                        "Could not resolve branch " + branch + " for " + owner + "/" + repo);
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> object = (Map<String, Object>) response.get("object");
            if (object == null || object.get("sha") == null) {
                throw new IllegalStateException(
                        "Malformed ref response resolving branch " + branch + " for " + owner + "/" + repo);
            }
            return (String) object.get("sha");
        });
    }

    /**
     * Creates a new branch (git ref) pointing at the given SHA — the session's throwaway
     * custom branch. A 422 from GitHub (ref already exists) is surfaced as a clean 409
     * ConflictException rather than an opaque 502 — realistically only reachable via the
     * TOCTOU race already handled at the persistence layer in RepoLinkingService, but mapped
     * cleanly here regardless.
     */
    public void createBranch(String token, String owner, String repo, String newBranchName, String fromSha) {
        try {
            GithubApiSupport.call("Create branch " + newBranchName + " on " + owner + "/" + repo, () -> {
                restClient.post()
                        .uri(GITHUB_API_BASE + "/repos/{owner}/{repo}/git/refs", owner, repo)
                        .header("Authorization", "Bearer " + token)
                        .header("Accept", "application/vnd.github+json")
                        .body(Map.of(
                                "ref", "refs/heads/" + newBranchName,
                                "sha", fromSha
                        ))
                        .retrieve()
                        .toBodilessEntity();
                return null;
            });
        } catch (GithubApiException e) {
            if (e.getCause() instanceof HttpClientErrorException.UnprocessableEntity) {
                throw new ConflictException(
                        "Branch " + newBranchName + " already exists on " + owner + "/" + repo);
            }
            throw e; // any other GitHub failure stays a 502, correctly
        }
    }
}