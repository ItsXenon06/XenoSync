package com.xenosync.model;
import jakarta.persistence.*;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "session_commits",
        indexes = @Index(name = "idx_session_commits_session_id", columnList = "session_id")
)
@Data
public class SessionCommits {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "triggered_by", nullable = false)
    private UUID triggeredBy;

    @Column(name = "commit_sha", nullable = false, length = 40)
    private String commitSha;

    @Column(name = "commit_message", nullable = false, length = 500)
    private String commitMessage;

    @Column(name = "created_at")
    private OffsetDateTime createdAt = OffsetDateTime.now();

    // getters and setters (or @Data, if using Lombok)
}