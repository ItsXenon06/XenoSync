package com.xenosync.model;
import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.UUID;
@Entity
@Table(
        name = "commit_votes",
        uniqueConstraints = @UniqueConstraint(columnNames = {"vote_id", "user_id"}),
        indexes = @Index(name = "idx_commit_vote_responses_vote_id", columnList = "vote_id")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
public class CommitVote {
    @EqualsAndHashCode.Include
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "proposed_by", nullable = false)
    private UUID proposedBy;

    @Column(name = "commit_message", nullable = false, length = 500)
    private String commitMessage;

    @Column(nullable = false, length = 10)
    private String status = "PENDING";

    @Column(name = "created_at")
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

}
