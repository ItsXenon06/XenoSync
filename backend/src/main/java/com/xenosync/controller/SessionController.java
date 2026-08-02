package com.xenosync.controller;

import com.xenosync.model.Session;
import com.xenosync.service.SessionService;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/v1/session")
public class SessionController {
    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }
    @PostMapping("/create")
    public Session createSession(@RequestBody UUID creatorId) {
        return sessionService.createSession(creatorId);
    }
    @PostMapping("/join/{sessionCode}")
    public Session joinSession(@PathVariable String sessionCode, @RequestParam UUID userId) {
        return sessionService.joinSession(sessionCode, userId);
    }
    @GetMapping("/{sessionCode}")
    public Session getSession(@PathVariable String sessionCode) {
        return sessionService.getSession(sessionCode);
    }
    @DeleteMapping("/leave/{sessionCode}")
    public Session leaveSession(@PathVariable String sessionCode, @RequestParam UUID userId) {
        return sessionService.leaveSession(sessionCode, userId);
    }
    @PutMapping("/close/{sessionCode}")
    public Session closeSession(@PathVariable String sessionCode) {
        return sessionService.closeSession(sessionCode);
    }
}
