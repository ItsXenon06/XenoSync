package com.xenosync.service;

import com.xenosync.exception.UnauthorizedException;
import com.xenosync.repository.SessionParticipantRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class SessionParticipantGuard {

    private final SessionParticipantRepository sessionParticipantRepository;

    public SessionParticipantGuard(SessionParticipantRepository sessionParticipantRepository) {
        this.sessionParticipantRepository = sessionParticipantRepository;
    }

    public void requireParticipant(UUID sessionId, UUID userId) {
        if (!sessionParticipantRepository.existsBySessionIdAndUserId(sessionId, userId)) {
            throw new UnauthorizedException("Not a participant of this session");
        }
    }
}