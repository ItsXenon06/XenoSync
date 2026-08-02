package com.xenosync.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "sessions")
@Data
public class Session {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, unique = true, length = 8)
    private String sessionCode;

    @Column(nullable = false, length = 255)
    private String joinLink;

    @Column(nullable = false)
    private UUID creatorId;

    @Column(nullable = false)
    private Integer participantCount = 1;

    @Column(nullable = false)
    private Integer maxCapacity = 4;

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
}