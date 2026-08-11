package com.xenosync.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

/**
 * Exchanges the GitHub App's own identity (its RS256 private key + app id) for a
 * short-lived per-installation access token — the credential that actually reads/writes
 * a specific repo, and what ends up (encrypted) in SessionLinkedRepo.githubAccessToken.
 *
 * Two distinct JWTs are in play here, don't confuse them with com.xenosync.security.JwtService's
 * user-facing access/refresh tokens:
 *   - The "app JWT" below: RS256, signed with the App's private key, proves "I am this GitHub App."
 *     Short-lived (GitHub caps this at 10 minutes), used ONLY to call the installation-token endpoint.
 *   - The installation access token GitHub hands back: what's actually used against the repo
 *     APIs afterward, and what gets persisted (encrypted) on SessionLinkedRepo.
 */
@Service
public class GithubAppAuthService {

    private static final String GITHUB_API_BASE = "https://api.github.com";

    private final PrivateKey appPrivateKey;
    private final String appId;
    private final RestClient restClient = RestClient.create();

    public GithubAppAuthService(
            @Value("${github.app.id}") String appId,
            @Value("${github.app.private-key-path}") String privateKeyPath
    ) {
        this.appId = appId;
        this.appPrivateKey = loadPrivateKey(privateKeyPath);
    }

    public record InstallationToken(String token, Instant expiresAt) {}

    /**
     * Calls POST /app/installations/{id}/access_tokens using a freshly-minted app JWT.
     * Caller (repo-linking service) is responsible for persisting the result and for
     * calling this again once github_token_expires_at has passed — this method has no
     * knowledge of any session and does no caching itself.
     */
    public InstallationToken createInstallationToken(String installationId) {
        return GithubApiSupport.call("GitHub installation token exchange", () -> {
            String appJwt = buildAppJwt();

            Map<String, Object> response = restClient.post()
                    .uri(GITHUB_API_BASE + "/app/installations/{id}/access_tokens", installationId)
                    .header("Authorization", "Bearer " + appJwt)
                    .header("Accept", "application/vnd.github+json")
                    .retrieve()
                    .body(Map.class);

            if (response == null || response.get("token") == null) {
                throw new IllegalStateException("GitHub did not return an installation token");
            }

            String token = (String) response.get("token");
            Instant expiresAt = Instant.parse((String) response.get("expires_at"));
            return new InstallationToken(token, expiresAt);
        });
    }

    private String buildAppJwt() {
        Instant now = Instant.now();
        return Jwts.builder()
                // GitHub recommends backdating iat by ~60s to tolerate clock drift.
                .issuedAt(Date.from(now.minusSeconds(60)))
                .expiration(Date.from(now.plusSeconds(600))) // GitHub's own 10-minute ceiling
                .issuer(appId)
                .signWith(appPrivateKey, Jwts.SIG.RS256)
                .compact();
    }

    private static PrivateKey loadPrivateKey(String path) {
        try {
            String pem = Files.readString(Path.of(path));
            String cleaned = pem
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                    .replace("-----END RSA PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");

            byte[] decoded = Base64.getDecoder().decode(cleaned);
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(decoded);
            return KeyFactory.getInstance("RSA").generatePrivate(keySpec);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read GitHub App private key at " + path, e);
        } catch (Exception e) {
            throw new IllegalStateException("Could not parse GitHub App private key — expected PKCS#8 PEM", e);
        }
    }
}