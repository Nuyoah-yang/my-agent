package com.example.super_biz_agent.domain;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChatSessionMetaEntity {
    /**
     * 会话标题表
     */
    private String sessionId;
    private Long userId;
    private String title;
    private LocalDateTime lastMessageAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
