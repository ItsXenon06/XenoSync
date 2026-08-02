package com.xenosync.model;
import jakarta.persistence.*;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;
@Entity
@Table(
        name = "plan_item_assignees",
        uniqueConstraints = @UniqueConstraint(columnNames = {"plan_item_id", "user_id"}),
        indexes = {
                @Index(name = "idx_plan_item_assignees_plan_item_id", columnList = "plan_item_id"),
                @Index(name = "idx_plan_item_assignees_user_id", columnList = "user_id")
        }
)
@Data
public class PlanItemAssignee {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "plan_item_id", nullable = false)
    private UUID planItemId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "assigned_at")
    private OffsetDateTime assignedAt = OffsetDateTime.now();

    // getters and setters (or @Data, if using Lombok)
}