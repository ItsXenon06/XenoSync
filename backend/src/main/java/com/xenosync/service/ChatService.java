package com.xenosync.service;

import com.xenosync.model.ChatMessage;
import com.xenosync.repository.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatMessageRepository chatMessageRepository;

    public ChatMessage sendMessage(UUID sessionId, UUID senderId, String content) {
        ChatMessage message = new ChatMessage();
        message.setSessionId(sessionId);
        message.setSenderId(senderId);
        message.setContent(content);
        message.setCreatedAt(OffsetDateTime.now());
        return chatMessageRepository.save(message);
    }

    public List<ChatMessage> getHistory(UUID sessionId) {
        return chatMessageRepository.findBySessionIdAndDeletedAtIsNullOrderByCreatedAtAsc(sessionId);
    }
}