package com.xenosync.dto;

import jakarta.validation.constraints.NotBlank;

public record LinkRepoRequest(
        @NotBlank String installationId,
        @NotBlank String repoOwner,
        @NotBlank String repoName,
        String sourceBranch // optional — falls back to repo default branch if blank
) {}