package com.example.super_biz_agent.service;

import com.example.super_biz_agent.dto.ApiResponse;
import com.example.super_biz_agent.dto.ChatRequest;
import org.springframework.stereotype.Service;


public interface ChatService {
    ApiResponse chat(ChatRequest request);
}
