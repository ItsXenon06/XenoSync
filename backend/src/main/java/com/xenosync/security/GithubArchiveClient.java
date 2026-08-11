package com.xenosync.security;

import com.xenosync.exception.GithubApiException;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Fetches an entire repo@ref as a single zip download (GitHub's zipball archive endpoint)
 * and extracts it in memory — one GitHub API call regardless of file count, replacing a
 * tree-walk + per-blob-fetch approach.
 *
 * Also sidesteps the git-trees "truncated" response entirely — that only applies to the
 * separate Trees API, not archive downloads, so very-large-file-count repos are not a
 * special case here.
 */
@Service
public class GithubArchiveClient {

    private static final String GITHUB_API_BASE = "https://api.github.com";
    private final RestClient restClient = RestClient.create();

    public record ExtractedFile(String path, byte[] content, boolean isDirectory) {}

    /**
     * Downloads and extracts the zipball. Entries are returned with the archive's synthetic
     * root folder (GitHub prefixes every entry with "{owner}-{repo}-{shortSha}/") stripped,
     * so callers get clean repo-relative paths.
     */
    public List<ExtractedFile> downloadAndExtract(String token, String owner, String repo, String ref) {
        byte[] zipBytes = GithubApiSupport.call(
                "Download archive for " + owner + "/" + repo + "@" + ref,
                () -> restClient.get()
                        .uri(GITHUB_API_BASE + "/repos/{owner}/{repo}/zipball/{ref}", owner, repo, ref)
                        .header("Authorization", "Bearer " + token)
                        .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                        .retrieve()
                        .body(byte[].class)
        );

        if (zipBytes == null || zipBytes.length == 0) {
            throw new GithubApiException(
                    "GitHub returned an empty archive for " + owner + "/" + repo + "@" + ref, null);
        }

        List<ExtractedFile> files = new ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String rawPath = entry.getName();
                String relativePath = stripRootFolder(rawPath);

                if (relativePath.isBlank()) {
                    continue; // the root folder entry itself
                }

                if (entry.isDirectory()) {
                    files.add(new ExtractedFile(relativePath, null, true));
                    continue;
                }

                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                zis.transferTo(buffer);
                files.add(new ExtractedFile(relativePath, buffer.toByteArray(), false));
            }
        } catch (IOException e) {
            // Local zip-parsing failure, not a GitHub API failure — correctly left out of
            // GithubApiSupport's scope, falls to the generic 500 handler instead of 502.
            throw new IllegalStateException("Failed to extract archive for " + owner + "/" + repo, e);
        }

        return files;
    }

    private static String stripRootFolder(String path) {
        int firstSlash = path.indexOf('/');
        return firstSlash < 0 ? "" : path.substring(firstSlash + 1);
    }
}