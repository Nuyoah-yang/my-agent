package com.example.super_biz_agent.dto;


import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChatSessionItem {
    private String sessionId;
    private String title;
    private LocalDateTime lastMessageAt;
    private LocalDateTime updatedAt;
    
}
