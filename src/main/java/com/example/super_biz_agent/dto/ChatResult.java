package com.example.super_biz_agent.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Service 层对话方法的返回结果，包含 AI 回答和会话 ID。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChatResult {
    private String answer;
    private String sessionId;
}
