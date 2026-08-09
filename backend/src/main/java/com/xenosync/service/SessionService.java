package com.xenosync.service;

import com.xenosync.constants.SessionConstants;
import com.xenosync.exception.ConflictException;
import com.xenosync.exception.NotFoundException;
import com.xenosync.exception.UnauthorizedException;
import com.xenosync.model.Session;
import com.xenosync.model.SessionParticipant;
import com.xenosync.model.SessionStatus;
import com.xenosync.repository.SessionLinkedRepoRepository;
import com.xenosync.repository.SessionParticipantRepository;
import com.xenosync.repository.SessionRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.OffsetDateTime;

import java.util.UUID;

@Transactional
@Service
public class SessionService {

    private final SessionRepository sessionRepository;
    private final SessionParticipantRepository sessionParticipantRepository;
    private final SessionLinkedRepoRepository sessionLinkedRepoRepository;
    //@Autowired
    public SessionService(SessionRepository sessionRepository, SessionParticipantRepository sessionParticipantRepository, SessionLinkedRepoRepository sessionLinkedRepoRepository) {
        this.sessionRepository = sessionRepository;
        this.sessionParticipantRepository = sessionParticipantRepository;
        this.sessionLinkedRepoRepository = sessionLinkedRepoRepository;
    }
    //private static final String ALPHANUMERIC_CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String ALPHANUMERIC_CHARACTERS =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    private static String generateRandomString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < SessionConstants.JOIN_CODE_LENGTH; i++) {
            sb.append(ALPHANUMERIC_CHARACTERS.charAt(
                    SECURE_RANDOM.nextInt(ALPHANUMERIC_CHARACTERS.length())
            ));
        }
        return sb.toString();
    }
    private String generateUniqueSessionCode() {
        String code;
        int attempts = 0;
        do {
            code = generateRandomString();
            attempts++;
            if (attempts > 5) {
                throw new ConflictException("Failed to generate a unique session code");
            }
        } while (sessionRepository.findBySessionCode(code).isPresent());
        return code;
    }

    public Session createSession(UUID creatorId) {
        String sessionCode = generateUniqueSessionCode();
        String joinLink = "xenosync.com/join/" + sessionCode;

        Session session = new Session();
        session.setSessionCode(sessionCode);
        session.setJoinLink(joinLink);
        session.setCreatorId(creatorId);
        session.setStatus(SessionStatus.ACTIVE);
        session.setMaxCapacity(SessionConstants.DEFAULT_MAX_CAPACITY);
        // participantCount intentionally not set here — self-corrected below
        // after the creator's participant row actually exists (Finding 28).

        Session savedSession = sessionRepository.save(session);

        SessionParticipant sessionParticipant = new SessionParticipant();
        sessionParticipant.setSessionId(savedSession.getId());
        sessionParticipant.setUserId(creatorId);
        sessionParticipantRepository.save(sessionParticipant);

        savedSession.setParticipantCount(
                (int) sessionParticipantRepository.countBySessionId(savedSession.getId())
        );
        return sessionRepository.save(savedSession);
    }

    public Session leaveSession(String sessionCode, UUID userId) {
        Session session = sessionRepository.findBySessionCode(sessionCode)
                .orElseThrow(() -> new NotFoundException("Session not found"));

        // Finding 24 — creator must close the session, not leave it, or the
        // session is orphaned (nobody left with authority to close it, per
        // Finding 25's creator-only check below).
        if (session.getCreatorId().equals(userId)) {
            throw new UnauthorizedException("Session creator cannot leave. Close the session instead.");
        }

        SessionParticipant participant = sessionParticipantRepository
                .findBySessionIdAndUserId(session.getId(), userId)
                .orElseThrow(() -> new NotFoundException("User is not in this session"));

        sessionParticipantRepository.delete(participant);

        session.setParticipantCount(
                (int) sessionParticipantRepository.countBySessionId(session.getId())
        ); //self-correcting

        return sessionRepository.save(session);
    }

    public Session joinSession(String sessionCode, UUID userId) {
        Session session = sessionRepository.findBySessionCode(sessionCode)
                .orElseThrow(() -> new NotFoundException("Session not found"));
        if (session.getStatus() != SessionStatus.ACTIVE) {
            throw new ConflictException("Session is not active");
        }
        if (sessionParticipantRepository.existsBySessionIdAndUserId(session.getId(), userId)) {
            throw new ConflictException("User already joined the session");
        }
        if (session.getParticipantCount() >= session.getMaxCapacity()) {
            throw new ConflictException("Session is full");
        }
        SessionParticipant participant = new SessionParticipant();
        participant.setSessionId(session.getId());
        participant.setUserId(userId);
        sessionParticipantRepository.save(participant);

        session.setParticipantCount(
                (int) sessionParticipantRepository.countBySessionId(session.getId())
        ); //self-correcting
        return sessionRepository.save(session);
    }

    public Session getSession(String sessionCode) {
        return sessionRepository.findBySessionCode(sessionCode)
                .orElseThrow(() -> new NotFoundException("Session not found"));
    }

    public boolean isRepoLinker(String sessionCode, UUID userId) {
        Session session = sessionRepository.findBySessionCode(sessionCode)
                .orElseThrow(() -> new NotFoundException("Session not found"));

        return sessionLinkedRepoRepository
                .findBySessionId(session.getId())
                .map(repo -> repo.getLinkedBy().equals(userId))
                .orElse(false);
    }

    public Session closeSession(String sessionCode, UUID requestingUserId) {
        Session session = sessionRepository.findBySessionCode(sessionCode)
                .orElseThrow(() -> new NotFoundException("Session not found"));

        // Finding 25 — only the creator may close the session.
        if (!session.getCreatorId().equals(requestingUserId)) {
            throw new UnauthorizedException("Only the session creator can close the session");
        }

        session.setStatus(SessionStatus.CLOSED);
        session.setClosedAt(OffsetDateTime.now());
        return sessionRepository.save(session);
    }
}