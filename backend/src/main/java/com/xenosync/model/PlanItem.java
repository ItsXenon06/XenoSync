package com.xenosync.model;
import jakarta.persistence.*;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "plan_items",
        indexes = @Index(name = "idx_plan_items_session_id", columnList = "session_id")
)
@Data
public class PlanItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 2000)
    private String description;

    @Column(nullable = false, length = 20)
    private String category = "TODO";

    @Column(name = "created_at")
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "edited_at")
    private OffsetDateTime editedAt;

    // getters and setters (or @Data, if using Lombok)
}