package com.xenosync.model;
import jakarta.persistence.*;
import lombok.*;

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
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
public class SessionCommitCoauthor {

    @EqualsAndHashCode.Include
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "commit_id", nullable = false)
    private UUID commitId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;


}