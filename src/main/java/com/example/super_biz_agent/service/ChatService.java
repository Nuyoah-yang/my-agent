package com.example.super_biz_agent.service;

import com.example.super_biz_agent.dto.ApiResponse;
import com.example.super_biz_agent.dto.ChatRequest;
import com.example.super_biz_agent.dto.ChatResponse;


public interface ChatService {
    ApiResponse<ChatResponse> chat(ChatRequest request);

}
