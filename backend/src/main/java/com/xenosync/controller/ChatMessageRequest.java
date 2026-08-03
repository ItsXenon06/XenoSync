package com.xenosync.controller;
import java.util.UUID;
public record ChatMessageRequest(UUID senderId, String content) {}
