package com.xenosync.service;

import com.xenosync.constants.SessionConstants;
import com.xenosync.dto.LinkRepoRequest;
import com.xenosync.exception.ConflictException;
import com.xenosync.exception.NotFoundException;
import com.xenosync.exception.UnauthorizedException;
import com.xenosync.model.Session;
import com.xenosync.model.SessionLinkedRepo;
import com.xenosync.repository.SessionLinkedRepoRepository;
import com.xenosync.repository.SessionRepository;
import com.xenosync.security.GithubAppAuthService;
import com.xenosync.security.GithubRepoClient;
import jakarta.transaction.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * AUTH_FULL.md Section 11 pattern: requireVerifiedEmail is called explicitly here, same as
 * SessionService's create/join should eventually call it (backlog #17) — this is the first
 * restricted-action endpoint actually wired to the guard.
 *
 * One-time link + one-time EAGER import (SessionFileImportService), by design — no ongoing
 * sync back to GitHub. The only thing that ever goes back to GitHub afterward is a push to
 * customBranch, which is a separate future feature (commit/vote flow), not this service.
 */
@Transactional
@Service
public class RepoLinkingService {

    private final SessionRepository sessionRepository;
    private final SessionParticipantGuard participantGuard;
    private final SessionLinkedRepoRepository sessionLinkedRepoRepository;
    private final AuthService authService;
    private final GithubAppAuthService githubAppAuthService;
    private final GithubRepoClient githubRepoClient;
    private final SessionFileImportService sessionFileImportService;

    public RepoLinkingService(
            SessionRepository sessionRepository,
            SessionParticipantGuard participantGuard,
            SessionLinkedRepoRepository sessionLinkedRepoRepository,
            AuthService authService,
            GithubAppAuthService githubAppAuthService,
            GithubRepoClient githubRepoClient,
            SessionFileImportService sessionFileImportService
    ) {
        this.sessionRepository = sessionRepository;
        this.participantGuard = participantGuard;
        this.sessionLinkedRepoRepository = sessionLinkedRepoRepository;
        this.authService = authService;
        this.githubAppAuthService = githubAppAuthService;
        this.githubRepoClient = githubRepoClient;
        this.sessionFileImportService = sessionFileImportService;
    }

    public SessionLinkedRepo linkRepo(String sessionCode, UUID userId, LinkRepoRequest req) {
        Session session = sessionRepository.findBySessionCode(sessionCode)
                .orElseThrow(() -> new NotFoundException("Session not found"));

        participantGuard.requireParticipant(session.getId(), userId);
        authService.requireVerifiedEmail(userId);

        if (sessionLinkedRepoRepository.findBySessionId(session.getId()).isPresent()) {
            throw new ConflictException("Session already has a linked repository");
        }

        // Step 1 — exchange installation id for a real, short-lived access token.
        GithubAppAuthService.InstallationToken installationToken =
                githubAppAuthService.createInstallationToken(req.installationId());

        // Step 2 — repo metadata (size, default branch fallback).
        GithubRepoClient.RepoMetadata metadata = githubRepoClient.fetchRepoMetadata(
                installationToken.token(), req.repoOwner(), req.repoName());

        String sourceBranch = (req.sourceBranch() == null || req.sourceBranch().isBlank())
                ? metadata.defaultBranch()
                : req.sourceBranch();

        // Step 3 — create the server-owned throwaway branch, deterministic per session.
        String customBranch = "xenosync/" + sessionCode;
        String sourceSha = githubRepoClient.resolveBranchSha(
                installationToken.token(), req.repoOwner(), req.repoName(), sourceBranch);
        githubRepoClient.createBranch(
                installationToken.token(), req.repoOwner(), req.repoName(), customBranch, sourceSha);

        // Step 4 — persist. githubAccessToken is encrypted transparently by GithubTokenConverter.
        SessionLinkedRepo repo = SessionLinkedRepo.builder()
                .sessionId(session.getId())
                .repoOwner(req.repoOwner())
                .repoName(req.repoName())
                .sourceBranch(sourceBranch)
                .customBranch(customBranch)
                .linkedBy(userId)
                .githubInstallationId(req.installationId())
                .githubAccessToken(installationToken.token())
                .githubTokenExpiresAt(installationToken.expiresAt().atOffset(java.time.ZoneOffset.UTC))
                .repoSizeKb(metadata.sizeKb())
                .fileCount(null) // filled in by the import step below
                .loadStrategy("EAGER")
                .build();

        SessionLinkedRepo saved;
        try {
            saved = sessionLinkedRepoRepository.save(repo);
        } catch (DataIntegrityViolationException e) {
            // Same TOCTOU class as Sections 2a/5.4/5.5 — two concurrent link attempts for the
            // same session both pass the pre-check above before either commits. The unique
            // constraint on session_id is the real authority; this is the race-loss response.
            throw new ConflictException("Session already has a linked repository");
        }

        // Step 5 — one-time EAGER import into session_files. Kept as its own service/transaction
        // boundary — see SessionFileImportService for why.
        sessionFileImportService.importRepo(saved, installationToken.token());

        return saved;
    }
}