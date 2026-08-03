package com.xenosync.model;

import jakarta.persistence.*;
import lombok.*;
import lombok.EqualsAndHashCode;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "session_file_contributions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"file_id", "user_id"}),
        indexes = {
                @Index(name = "idx_session_file_contributions_session_id", columnList = "session_id"),
                @Index(name = "idx_session_file_contributions_file_id", columnList = "file_id"),
                @Index(name = "idx_session_file_contributions_user_id", columnList = "user_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
public class SessionFileContribution {

    @EqualsAndHashCode.Include
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "file_id", nullable = false)
    private UUID fileId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "net_chars_added", nullable = false)
    private int netCharsAdded = 0;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    // getters and setters (or @Data, if using Lombok)
}