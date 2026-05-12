package com.example.super_biz_agent.controller;

import com.example.super_biz_agent.dto.ApiResponse;
import com.example.super_biz_agent.dto.ChatMessageItem;
import com.example.super_biz_agent.dto.ChatRequest;
import com.example.super_biz_agent.dto.ChatResponse;
import com.example.super_biz_agent.dto.ChatSessionItem;
import com.example.super_biz_agent.service.ChatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@Slf4j
@RestController
@RequestMapping("/api/chat")
public class ChatController {
    @Autowired
    private ChatService chatService;

    @PostMapping
    public ApiResponse<ChatResponse> chat(@RequestBody ChatRequest request){
        log.info("参数为:{}",request.getMessage());
        return chatService.chat(request);
    }

    @GetMapping("/sessions")
    public ApiResponse<List<ChatSessionItem>> listSessions() {
        try {
            return ApiResponse.success(chatService.listSessions());
        } catch (SecurityException | IllegalArgumentException e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @GetMapping("/messages")
    public ApiResponse<List<ChatMessageItem>> listMessages(@RequestParam("sessionId") String sessionId) {
        try {
            return ApiResponse.success(chatService.listMessages(sessionId));
        } catch (SecurityException | IllegalArgumentException e) {
            return ApiResponse.error(e.getMessage());
        }
    }

}
