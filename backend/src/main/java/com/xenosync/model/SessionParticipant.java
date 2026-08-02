package com.xenosync.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "session_participants",
        uniqueConstraints = @UniqueConstraint(columnNames = {"session_id", "user_id"}),
        indexes = {
                @Index(name = "idx_session_participants_session_id", columnList = "session_id"),
                @Index(name = "idx_session_participants_user_id", columnList = "user_id")
        }
)
@Data
public class SessionParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "joined_at")
    private OffsetDateTime joinedAt = OffsetDateTime.now();

    // getters and setters (or @Data, if using Lombok)
}