package com.xenosync.repository;

import com.xenosync.model.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {
    // Format chat order (oldest first)
    List<ChatMessage> findBySessionIdAndDeletedAtIsNullOrderByCreatedAtAsc(UUID sessionId);
}