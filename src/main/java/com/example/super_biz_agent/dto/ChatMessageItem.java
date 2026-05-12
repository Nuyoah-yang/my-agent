package com.example.super_biz_agent.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChatMessageItem {
    private Long id;
    private String sessionId;
    private Integer role;
    private String content;
    private LocalDateTime createdAt;
}
