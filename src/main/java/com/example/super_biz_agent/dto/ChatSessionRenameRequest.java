package com.example.super_biz_agent.dto;

import lombok.Data;

@Data
public class ChatSessionRenameRequest {
    /**
     * 会话新标题。
     */
    private String title;
}
