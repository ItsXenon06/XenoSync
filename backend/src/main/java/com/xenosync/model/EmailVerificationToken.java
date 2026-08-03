package com.xenosync.model;
import jakarta.persistence.*;
import lombok.Data;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "email_verification_tokens",
        indexes = @Index(name = "idx_email_verification_tokens_user_id", columnList = "user_id")
)
@Data
public class EmailVerificationToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, unique = true)
    private String token;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "used_at")
    private OffsetDateTime usedAt;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    // getters and setters (or @Data, if using Lombok)
}