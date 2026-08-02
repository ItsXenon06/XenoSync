package com.xenosync.repository;
import com.xenosync.model.SessionParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface SessionParticipantRepository extends JpaRepository<SessionParticipant, UUID> {

    boolean existsBySessionIdAndUserId(UUID sessionId, UUID userId);

    Optional<SessionParticipant> findBySessionIdAndUserId(UUID sessionId, UUID userId);

    long countBySessionId(UUID sessionId);

    void deleteBySessionIdAndUserId(UUID sessionId, UUID userId);
}