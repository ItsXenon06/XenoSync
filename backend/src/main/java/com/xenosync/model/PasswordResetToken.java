package com.xenosync.model;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.UUID;
@Entity
@Table(
        name = "password_reset_tokens",
        indexes = @Index(name = "idx_password_reset_tokens_user_id", columnList = "user_id")
)
@Data
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, unique = true, length = 255)
    private String token;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "used_at")
    private OffsetDateTime usedAt;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    // getters and setters (or @Data, if using Lombok)
}