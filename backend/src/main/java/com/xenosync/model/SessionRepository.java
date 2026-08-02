package com.xenosync.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "session_repositories")
@Data
public class SessionRepository {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "session_id", nullable = false, unique = true)
    private UUID sessionId;

    @Column(name = "repo_owner", nullable = false, length = 100)
    private String repoOwner;

    @Column(name = "repo_name", nullable = false, length = 100)
    private String repoName;

    @Column(name = "source_branch", nullable = false, length = 100)
    private String sourceBranch = "main";

    @Column(name = "custom_branch", nullable = false, length = 100)
    private String customBranch;

    @Column(name = "linked_by", nullable = false)
    private UUID linkedBy;

    @Column(name = "github_access_token", nullable = false)
    private String githubAccessToken;

    @Column(name = "repo_size_kb")
    private Integer repoSizeKb;

    @Column(name = "file_count")
    private Integer fileCount;

    @Column(name = "load_strategy", nullable = false, length = 10)
    private String loadStrategy = "EAGER";

    @Column(name = "created_at")
    private OffsetDateTime createdAt = OffsetDateTime.now();

    // getters and setters (or @Data, if using Lombok)
}