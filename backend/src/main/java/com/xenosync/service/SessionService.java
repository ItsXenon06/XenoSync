package com.xenosync.service;
import com.xenosync.constants.SessionConstants;
import com.xenosync.model.Session;
import com.xenosync.model.SessionParticipant;
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
                throw new RuntimeException("Failed to generate a unique session code");
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
            session.setStatus("ACTIVE");
            session.setParticipantCount(1);
            session.setMaxCapacity(SessionConstants.DEFAULT_MAX_CAPACITY);

            Session savedSession = sessionRepository.save(session);

            SessionParticipant sessionParticipant = new SessionParticipant();
            sessionParticipant.setSessionId(savedSession.getId());
            sessionParticipant.setUserId(creatorId);
            sessionParticipantRepository.save(sessionParticipant);

            return savedSession;
    }

    public Session leaveSession(String sessionCode, UUID userId) {
        Session session = sessionRepository.findBySessionCode(sessionCode)
                .orElseThrow(() -> new RuntimeException("Session not found"));

        SessionParticipant participant = sessionParticipantRepository
                .findBySessionIdAndUserId(session.getId(), userId)
                .orElseThrow(() -> new RuntimeException("User is not in this session"));

        sessionParticipantRepository.delete(participant);

        session.setParticipantCount(
                (int) sessionParticipantRepository.countBySessionId(session.getId())
        ); //self-correcting

        return sessionRepository.save(session);
    }

    public Session joinSession(String sessionCode, UUID userId) {
        Session session = sessionRepository.findBySessionCode(sessionCode)
                .orElseThrow(() -> new RuntimeException("Session not found"));
        if (!session.getStatus().equals("ACTIVE")) {
            throw new RuntimeException("Session is not active");
        }
        if(sessionParticipantRepository.existsBySessionIdAndUserId(session.getId(), userId)) {
            throw new RuntimeException("User already joined the session");
        }
        if (session.getParticipantCount() >= session.getMaxCapacity()) {
            throw new RuntimeException("Session is full");
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
        Session session = sessionRepository.findBySessionCode(sessionCode)
                .orElseThrow(() -> new RuntimeException("Session not found"));
        return session;
    }

    public boolean isRepoLinker(String sessionCode, UUID userId) {
        Session session = sessionRepository.findBySessionCode(sessionCode)
                .orElseThrow(() -> new RuntimeException("Session not found"));

        return sessionLinkedRepoRepository
                .findBySessionId(session.getId())
                .map(repo -> repo.getLinkedBy().equals(userId))
                .orElse(false);
    }

    public Session closeSession(String sessionCode) {
        Session session = sessionRepository.findBySessionCode(sessionCode)
                .orElseThrow(() -> new RuntimeException("Session not found"));
        session.setStatus("CLOSED");
        session.setClosedAt(OffsetDateTime.now());
        return sessionRepository.save(session);
    }
}
