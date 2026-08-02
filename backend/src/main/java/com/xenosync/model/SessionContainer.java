package com.xenosync.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "session_containers",
        indexes = {
                @Index(name = "idx_session_containers_session_id", columnList = "session_id"),
                @Index(name = "idx_session_containers_status", columnList = "status")
        }
)
@Data
public class SessionContainer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "session_id", nullable = false, unique = true)
    private UUID sessionId;

    @Column(name = "app_container_id", columnDefinition = "TEXT")
    private String appContainerId;

    @Column(name = "db_container_id", columnDefinition = "TEXT")
    private String dbContainerId;

    @Column(name = "live_url", columnDefinition = "TEXT")
    private String liveUrl;

    @Column(name = "host_port")
    private Integer hostPort;

    @Column(columnDefinition = "TEXT")
    private String stack;

    @Column(name = "db_type", columnDefinition = "TEXT")
    private String dbType;

    @Column(nullable = false, length = 10)
    private String status = "STOPPED";

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "stopped_at")
    private OffsetDateTime stoppedAt;

    @Column(name = "last_active_at")
    private OffsetDateTime lastActiveAt;

    // getters and setters (or @Data, if using Lombok)
}