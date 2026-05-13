package com.example.super_biz_agent.service;

public interface ChatMessagePersistenceService {
    /**
     * 保存对话信息
     * @param sessionId
     * @param userId
     * @param question
     * @param answer
     */
    void saveRoundMessages(String sessionId, Long userId, String question, String answer);
}
