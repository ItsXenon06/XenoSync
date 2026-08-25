package com.xenosync.controller;

import com.xenosync.model.ChatMessage;
import com.xenosync.service.ChatService;
import com.xenosync.service.SessionParticipantGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/sessions/{sessionId}/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final SessionParticipantGuard participantGuard;

    private UUID resolveUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new org.springframework.security.access.AccessDeniedException("Not authenticated");
        }
        return (UUID) auth.getPrincipal();
    }

    @PostMapping
    public ChatMessage sendMessage(
            @PathVariable UUID sessionId,
            @RequestBody ChatMessageRequest request
    ) {
        UUID senderId = resolveUserId();
        participantGuard.requireParticipant(sessionId, senderId);
        return chatService.sendMessage(sessionId, senderId, request.content());
    }

    @GetMapping
    public List<ChatMessage> getHistory(@PathVariable UUID sessionId) {
        UUID userId = resolveUserId();
        participantGuard.requireParticipant(sessionId, userId);
        return chatService.getHistory(sessionId);
    }
}