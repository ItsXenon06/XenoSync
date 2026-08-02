package com.xenosync.model;
import jakarta.persistence.*;
import lombok.Data;

import java.util.UUID;

@Entity
@Table(
        name = "session_commit_coauthors",
        uniqueConstraints = @UniqueConstraint(columnNames = {"commit_id", "user_id"}),
        indexes = {
                @Index(name = "idx_session_commit_coauthors_commit_id", columnList = "commit_id"),
                @Index(name = "idx_session_commit_coauthors_user_id", columnList = "user_id")
        }
)
@Data
public class SessionCommitCoauthor {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "commit_id", nullable = false)
    private UUID commitId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    // getters and setters (or @Data, if using Lombok)
}