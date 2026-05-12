package com.example.super_biz_agent.service;

public interface ChatMessagePersistenceService {
    void saveRoundMessages(String sessionId, Long userId, String question, String answer);
}
