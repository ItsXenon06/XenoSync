package com.xenosync.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record LinkedRepoResponse(
        UUID id,
        UUID sessionId,
        String repoOwner,
        String repoName,
        String sourceBranch,
        String customBranch,
        Integer repoSizeKb,
        Integer fileCount,
        String loadStrategy,
        OffsetDateTime createdAt
) {
    public static LinkedRepoResponse from(com.xenosync.model.SessionLinkedRepo repo) {
        return new LinkedRepoResponse(
                repo.getId(),
                repo.getSessionId(),
                repo.getRepoOwner(),
                repo.getRepoName(),
                repo.getSourceBranch(),
                repo.getCustomBranch(),
                repo.getRepoSizeKb(),
                repo.getFileCount(),
                repo.getLoadStrategy(),
                repo.getCreatedAt()
        );
    }
}