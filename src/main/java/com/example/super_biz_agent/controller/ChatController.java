package com.example.super_biz_agent.controller;

import com.example.super_biz_agent.dto.ApiResponse;
import com.example.super_biz_agent.dto.ChatMessageItem;
import com.example.super_biz_agent.dto.ChatRequest;
import com.example.super_biz_agent.dto.ChatResponse;
import com.example.super_biz_agent.dto.ChatSessionRenameRequest;
import com.example.super_biz_agent.dto.ChatSessionItem;
import com.example.super_biz_agent.service.ChatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

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
        // 异常统一交给 GlobalExceptionHandler 处理，这里只保留主流程。
        return ApiResponse.success(chatService.listSessions());
    }

    @GetMapping("/messages")
    public ApiResponse<List<ChatMessageItem>> listMessages(@RequestParam("sessionId") String sessionId) {
        // 统一异常处理后，Controller 不再重复 try-catch。
        return ApiResponse.success(chatService.listMessages(sessionId));
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ApiResponse<String> deleteSession(@PathVariable("sessionId") String sessionId) {
        // 删除成功返回固定文案；删除失败由全局异常处理器返回标准错误结构。
        chatService.deleteSession(sessionId);
        return ApiResponse.success("删除成功");
    }

    @PatchMapping("/sessions/{sessionId}/title")
    public ApiResponse<String> renameSession(@PathVariable("sessionId") String sessionId,
                                             @RequestBody ChatSessionRenameRequest request) {
        // 仅更新标题字段，详细参数校验与归属校验在 service 层完成。
        chatService.renameSession(sessionId, request.getTitle());
        return ApiResponse.success("重命名成功");
    }

    @PostMapping(value = "/chat_stream", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter chatStream(@RequestBody ChatRequest request) {
        return chatService.chatStream(request);
    }

}
