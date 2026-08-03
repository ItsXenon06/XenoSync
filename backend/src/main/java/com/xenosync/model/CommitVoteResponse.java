package com.xenosync.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "commit_vote_responses",
        uniqueConstraints = @UniqueConstraint(columnNames = {"vote_id", "user_id"}),
        indexes = {
                @Index(name = "idx_commit_vote_responses_vote_id", columnList = "vote_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
public class CommitVoteResponse {

    @EqualsAndHashCode.Include
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "vote_id", nullable = false)
    private UUID voteId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 10)
    private String response; // 'APPROVE', 'REJECT'

    @Column(name = "voted_at")
    private OffsetDateTime votedAt = OffsetDateTime.now();
}