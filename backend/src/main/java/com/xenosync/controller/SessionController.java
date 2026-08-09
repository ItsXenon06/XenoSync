package com.xenosync.controller;

import com.xenosync.model.Session;
import com.xenosync.service.SessionService;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/v1/session")
public class SessionController {

    private UUID resolveUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new org.springframework.security.access.AccessDeniedException("Not authenticated");
        }
        return (UUID) auth.getPrincipal();
    }

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @PostMapping("/create")
    public Session createSession() {
        return sessionService.createSession(resolveUserId());
    }

    @PostMapping("/join/{sessionCode}")
    public Session joinSession(@PathVariable String sessionCode) {
        return sessionService.joinSession(sessionCode, resolveUserId());
    }

    @GetMapping("/{sessionCode}")
    public Session getSession(@PathVariable String sessionCode) {
        return sessionService.getSession(sessionCode);
    }

    /**
     * Returns LeaveSessionResult containing the updated session and a repoLinkerWarning flag.
     * Frontend should check repoLinkerWarning — if true, a 10-second confirm prompt
     * should have been shown before this call was made.
     */
    @GetMapping("/{sessionCode}/leave-warning")
    public boolean getLeaveWarning(@PathVariable String sessionCode) {
        return sessionService.isRepoLinker(sessionCode, resolveUserId());
    }

    /**
     * Deletes the participant and returns the updated session.
     * Frontend should call GET /{sessionCode}/leave-warning first — if true,
     * show a confirm prompt before calling this endpoint.
     */
    @DeleteMapping("/leave/{sessionCode}")
    public Session leaveSession(@PathVariable String sessionCode) {
        return sessionService.leaveSession(sessionCode, resolveUserId());
    }

    @PutMapping("/close/{sessionCode}")
    public Session closeSession(@PathVariable String sessionCode) {
        return sessionService.closeSession(sessionCode, resolveUserId());
    }
}