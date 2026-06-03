package com.example.super_biz_agent.service;

import com.example.super_biz_agent.dto.ChatMessageItem;
import com.example.super_biz_agent.dto.ChatRequest;
import com.example.super_biz_agent.dto.ChatResult;
import com.example.super_biz_agent.dto.ChatSessionItem;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;


public interface ChatService {
    /**
     * 处理对话请求，返回 AI 回答内容和会话 ID。
     * 参数校验失败抛出 IllegalArgumentException，
     * 会话归属校验失败抛出 SecurityException，
     * 模型调用失败抛出 RuntimeException。
     */
    ChatResult chat(ChatRequest request);

    List<ChatSessionItem> listSessions();

    List<ChatMessageItem> listMessages(String sessionId);

    void deleteSession(String sessionId);

    void renameSession(String sessionId, String title);

    /**
     * 流式传输
     * @param request
     * @return
     */
    SseEmitter chatStream(ChatRequest request);
}
