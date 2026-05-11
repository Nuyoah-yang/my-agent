package com.example.super_biz_agent.service.serviceImpl;

import com.ethlo.time.DateTime;
import com.example.super_biz_agent.dto.ApiResponse;
import com.example.super_biz_agent.dto.ChatRequest;
import com.example.super_biz_agent.dto.ChatResponse;
import com.example.super_biz_agent.service.ChatService;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;


@Service
public class ChatServiceImpl implements ChatService {
    private static final Logger logger = LoggerFactory.getLogger(ChatServiceImpl.class);
    private static final long SESSION_TIMEOUT_MS = 30 * 60 * 1000; // 30分钟超时

    private final ChatClient chatClient;
    ConcurrentHashMap<String, ChatMemory> sessionMap = new ConcurrentHashMap<>();
    ConcurrentHashMap<String, Long> sessionLastAccessTime = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleanupExecutor = Executors.newSingleThreadScheduledExecutor();

    public ChatServiceImpl(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }


    @Override
    public ApiResponse chat(ChatRequest request) {
       try{
           logger.info("收到对话请求 - SessionId: {}, Question: {}", request.getSessionId(), request.getMessage());

            //参数校验
           if(request.getMessage()==null||request.getMessage().trim().isEmpty()){
               logger.warn("message内容为空");
               return ApiResponse.success(ChatResponse.error("问题内容不能为空"));
           }

           if(request.getSessionId()==null || request.getSessionId().trim().isEmpty()){
               request.setSessionId(UUID.randomUUID().toString());
           }
           ChatMemory memory = sessionMap.computeIfAbsent(
                   request.getSessionId(),
                   k -> MessageWindowChatMemory.builder()
                           .maxMessages(10)
                           .build()
           );

           String answer = chatClient.prompt()
                   .user(request.getMessage())
                   .advisors(MessageChatMemoryAdvisor.builder(memory)
                           .conversationId(request.getSessionId())
                           .build())
                   .call()
                   .content();

           return ApiResponse.success(ChatResponse.success(answer));
       }catch (Exception e){
           logger.error("对话失败",e);
           return ApiResponse.success(ChatResponse.error("模型调用失败: " + e.getMessage()));
       }
    }


}
