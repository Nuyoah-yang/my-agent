package com.example.super_biz_agent.service;

import com.example.super_biz_agent.dto.ApiResponse;
import com.example.super_biz_agent.dto.ChatMessageItem;
import com.example.super_biz_agent.dto.ChatRequest;
import com.example.super_biz_agent.dto.ChatResponse;
import com.example.super_biz_agent.dto.ChatSessionItem;

import java.util.List;


public interface ChatService {
    ApiResponse<ChatResponse> chat(ChatRequest request);

    List<ChatSessionItem> listSessions();

    List<ChatMessageItem> listMessages(String sessionId);

    void deleteSession(String sessionId);

    void renameSession(String sessionId, String title);
}
