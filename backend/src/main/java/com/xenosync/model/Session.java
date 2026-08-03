package com.xenosync.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "sessions",
        indexes = @Index(name = "idx_sessions_creator_id", columnList = "creator_id")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
public class Session {

    @EqualsAndHashCode.Include
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "session_code", nullable = false, unique = true, length = 8)
    private String sessionCode;

    @Column(name = "join_link", nullable = false)
    private String joinLink;

    @Column(name = "creator_id", nullable = false)
    private UUID creatorId;

    @Column(name = "participant_count")
    private Integer participantCount = 1;

    @Column(name = "max_capacity")
    private Integer maxCapacity = 4;

    @Column(length = 20)
    private String status = "ACTIVE";

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    // getters and setters (or @Data, if using Lombok)
}