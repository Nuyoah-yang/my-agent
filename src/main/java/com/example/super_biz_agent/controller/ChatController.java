package com.example.super_biz_agent.controller;

import com.example.super_biz_agent.dto.ApiResponse;
import com.example.super_biz_agent.dto.ChatRequest;
import com.example.super_biz_agent.service.ChatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@Slf4j
@RestController
@RequestMapping("/api/chat")
public class ChatController {
    @Autowired
    private ChatService chatService;

    @PostMapping
    public ApiResponse chat(@RequestBody ChatRequest request){
        log.info("参数为:{}",request.getMessage());
        return chatService.chat(request);
    }


}
