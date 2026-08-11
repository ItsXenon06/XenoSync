package com.xenosync.controller;

import com.xenosync.dto.LinkRepoRequest;
import com.xenosync.dto.LinkedRepoResponse;
import com.xenosync.service.RepoLinkingService;
import jakarta.validation.Valid;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/v1/session/{sessionCode}/repo")
public class RepoController {

    private final RepoLinkingService repoLinkingService;

    public RepoController(RepoLinkingService repoLinkingService) {
        this.repoLinkingService = repoLinkingService;
    }

    @PostMapping
    public LinkedRepoResponse linkRepo(
            @PathVariable String sessionCode,
            @Valid @RequestBody LinkRepoRequest req
    ) {
        UUID userId = resolveUserId();
        var repo = repoLinkingService.linkRepo(sessionCode, userId, req);
        return LinkedRepoResponse.from(repo);
    }

    private UUID resolveUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new org.springframework.security.access.AccessDeniedException("Not authenticated");
        }
        return (UUID) auth.getPrincipal();
    }
}