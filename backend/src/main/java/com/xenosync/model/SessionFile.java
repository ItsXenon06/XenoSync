package com.xenosync.model;
import jakarta.persistence.*;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "session_files",
        uniqueConstraints = @UniqueConstraint(columnNames = {"session_id", "file_path"}),
        indexes = @Index(name = "idx_session_files_session_id", columnList = "session_id")
)
@Data
public class SessionFile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    @Lob
    @Column(name = "content")
    private byte[] content;

    @Column(name = "is_binary", nullable = false)
    private boolean isBinary = false;

    @Column(name = "created_at")
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "edited_at")
    private OffsetDateTime editedAt;

    // getters and setters (or @Data, if using Lombok)
}