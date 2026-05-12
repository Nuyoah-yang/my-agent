package com.example.super_biz_agent.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessageEntity {
    /**
     * 聊天消息明细表 实体
     * 对应数据库表：chat_message
     */
    // 消息ID（自增主键）
    private Long id;
    // 会话ID
    private String sessionId;
    // 用户ID
    private Long userId;
    // 消息角色：0-system 1-user 2-assistant
    private Integer role;
    // 消息内容
    private String content;
    // 创建时间
    private LocalDateTime createdAt;
}
