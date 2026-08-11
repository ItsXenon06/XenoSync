package com.xenosync.service;

import com.xenosync.model.SessionFile;
import com.xenosync.model.SessionLinkedRepo;
import com.xenosync.repository.SessionFileRepository;
import com.xenosync.repository.SessionLinkedRepoRepository;
import com.xenosync.security.GithubArchiveClient;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

/**
 * One-time EAGER import: single zip download -> session_files, at link time only.
 * No ongoing sync — session_files and GitHub diverge freely after this until a future
 * push-to-custom-branch feature.
 */
@Transactional
@Service
public class SessionFileImportService {

    private static final long PER_FILE_SKIP_BYTES = 5L * 1024 * 1024;      // 5MB
    private static final long TOTAL_IMPORT_BYTES = 500L * 1024 * 1024;     // 500MB

    private static final Set<String> BINARY_EXTENSIONS = Set.of(
            "png", "jpg", "jpeg", "gif", "webp", "ico", "bmp", "tiff",
            "mp3", "mp4", "wav", "ogg", "mov", "avi",
            "zip", "tar", "gz", "7z", "rar",
            "pdf", "woff", "woff2", "ttf", "eot", "otf",
            "so", "dll", "dylib", "class", "jar", "exe", "bin"
    );

    private final GithubArchiveClient githubArchiveClient;
    private final SessionFileRepository sessionFileRepository;
    private final SessionLinkedRepoRepository sessionLinkedRepoRepository;

    public SessionFileImportService(
            GithubArchiveClient githubArchiveClient,
            SessionFileRepository sessionFileRepository,
            SessionLinkedRepoRepository sessionLinkedRepoRepository
    ) {
        this.githubArchiveClient = githubArchiveClient;
        this.sessionFileRepository = sessionFileRepository;
        this.sessionLinkedRepoRepository = sessionLinkedRepoRepository;
    }

    public void importRepo(SessionLinkedRepo repo, String installationToken) {
        List<GithubArchiveClient.ExtractedFile> extracted = githubArchiveClient.downloadAndExtract(
                installationToken, repo.getRepoOwner(), repo.getRepoName(), repo.getSourceBranch());

        long importedBytesSoFar = 0;
        int fileCount = 0;

        for (GithubArchiveClient.ExtractedFile ef : extracted) {
            if (ef.isDirectory()) {
                continue; // session_files models files only, directories are implied by paths
            }
            fileCount++;

            boolean isBinary = isBinaryPath(ef.path());
            long size = ef.content().length;
            boolean overPerFileLimit = size > PER_FILE_SKIP_BYTES;
            boolean overTotalBudget = importedBytesSoFar >= TOTAL_IMPORT_BYTES;

            SessionFile file = SessionFile.builder()
                    .sessionId(repo.getSessionId())
                    .filePath(ef.path())
                    .isBinary(isBinary)
                    .createdAt(OffsetDateTime.now())
                    .build();

            if (isBinary || overPerFileLimit || overTotalBudget) {
                file.setContent(null); // path-only row; fetch-on-open covers this later
            } else {
                file.setContent(ef.content());
                importedBytesSoFar += size;
            }

            sessionFileRepository.save(file);
        }

        repo.setFileCount(fileCount);
        sessionLinkedRepoRepository.save(repo);
    }

    private static boolean isBinaryPath(String path) {
        int dot = path.lastIndexOf('.');
        if (dot < 0 || dot == path.length() - 1) {
            return false;
        }
        return BINARY_EXTENSIONS.contains(path.substring(dot + 1).toLowerCase());
    }
}