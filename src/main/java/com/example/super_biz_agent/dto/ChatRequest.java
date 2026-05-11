package com.example.super_biz_agent.dto;
import lombok.Data;

@Data
public class ChatRequest {
    private String sessionId;
    private String message;
}
