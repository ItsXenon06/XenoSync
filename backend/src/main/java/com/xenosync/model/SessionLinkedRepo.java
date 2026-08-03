package com.xenosync.model;


//DEV NOTE: THIS IS THE CORRESPONDING ENTITY FOR SESSIONREPOSITORY TABLE



import jakarta.persistence.*;
import lombok.*;
import lombok.EqualsAndHashCode;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "session_repositories") //KEEP THE NAME PER DEV NOTE
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"githubAccessToken"})
public class SessionLinkedRepo {

    @EqualsAndHashCode.Include
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

    @Column(name = "github_installation_id", length = 50)
    private String githubInstallationId;

    @Column(name = "github_token_expires_at")
    private OffsetDateTime githubTokenExpiresAt;

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

}