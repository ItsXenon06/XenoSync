package com.xenosync.controller;

import com.xenosync.model.ChatMessage;
import com.xenosync.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST endpoints for session chat.
 * Note: this is REST-only (send + fetch history), not real-time.
 * Live delivery via WebSocket/STOMP is a separate piece, not yet wired here.
 */

@RestController
@RequestMapping("/api/sessions/{sessionId}/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping
    public ChatMessage sendMessage(
            @PathVariable UUID sessionId,
            @RequestBody ChatMessageRequest request,
            HttpSession session) {
        UUID senderId = (UUID) session.getAttribute("userId"); // whatever key you use at login
        return chatService.sendMessage(sessionId, senderId, request.content());
    }
    /**
     * Returns full chat history for a session, oldest first.
     * Soft-deleted messages (deletedAt set) are excluded at the repository level.
     */
    @GetMapping
    public List<ChatMessage> getHistory(@PathVariable UUID sessionId) {
        return chatService.getHistory(sessionId);
    }
}