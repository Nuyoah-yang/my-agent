package com.example.super_biz_agent.service.serviceImpl;


import com.example.super_biz_agent.domain.ChatSessionMetaEntity;
import com.example.super_biz_agent.dto.ApiResponse;
import com.example.super_biz_agent.dto.ChatMessageItem;
import com.example.super_biz_agent.dto.ChatRequest;
import com.example.super_biz_agent.dto.ChatResponse;
import com.example.super_biz_agent.dto.ChatSessionItem;
import com.example.super_biz_agent.mapper.ChatMessageMapper;
import com.example.super_biz_agent.mapper.ChatSessionMetaMapper;
import com.example.super_biz_agent.memory.ChatMemoryHelper;
import com.example.super_biz_agent.memory.RedisChatMemoryRepository;
import com.example.super_biz_agent.security.UserContextHolder;
import com.example.super_biz_agent.service.ChatMessagePersistenceService;
import com.example.super_biz_agent.service.ChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.ai.chat.messages.Message;


@Service
public class ChatServiceImpl implements ChatService {
    private static final Logger logger = LoggerFactory.getLogger(ChatServiceImpl.class);

    private final ChatClient chatClient;
    private final ChatMemoryRepository chatMemoryRepository;
    private final ChatMemoryHelper chatMemoryHelper;
    private final ChatMessageMapper chatMessageMapper;
    private final ChatSessionMetaMapper chatSessionMetaMapper;
    private final ChatMessagePersistenceService chatMessagePersistenceService;

    @Value("${chat.memory.max-messages:10}")
    private int maxMessages;

    public ChatServiceImpl(ChatClient.Builder chatClientBuilder,
                           RedisChatMemoryRepository chatMemoryRepository,
                           ChatMemoryHelper chatMemoryHelper,
                           ChatMessageMapper chatMessageMapper,
                           ChatSessionMetaMapper chatSessionMetaMapper,
                           ChatMessagePersistenceService chatMessagePersistenceService
                           ) {
        this.chatClient = chatClientBuilder.build();
        this.chatMemoryRepository = chatMemoryRepository;
        this.chatMemoryHelper = chatMemoryHelper;
        this.chatMessageMapper = chatMessageMapper;
        this.chatSessionMetaMapper = chatSessionMetaMapper;
        this.chatMessagePersistenceService=chatMessagePersistenceService;
    }

    @Override
    public ApiResponse<ChatResponse> chat(ChatRequest request) {
       try{
           logger.info("收到对话请求 - SessionId: {}, Question: {}", request.getSessionId(), request.getMessage());
            // 参数校验：问题内容不能为空
           if(request.getMessage()==null||request.getMessage().trim().isEmpty()){
               logger.warn("message内容为空");
               return ApiResponse.success(ChatResponse.error("问题内容不能为空",request.getSessionId()));
           }

           // 当前用户来自 JWT 解析结果，用于会话归属校验
           Long currentUserId = UserContextHolder.getUserId();
           if (currentUserId == null) {
               return ApiResponse.error("未登录或登录已过期");
           }

           // 归一化 sessionId（为空则生成），并写入/校验会话归属元数据
           String sessionId = chatMemoryHelper.normalizeSessionId(request.getSessionId());
           upsertAndValidateSessionOwnership(sessionId, currentUserId, request.getMessage());

           // 聊天记忆窗口由 MessageWindowChatMemory 控制（当前 10 条）
           ChatMemory chatMemory = MessageWindowChatMemory.builder()
                   .chatMemoryRepository(chatMemoryRepository)
                   .maxMessages(maxMessages)
                   .build();

           // 读取历史并构造上下文提示，保证多轮连贯
           List<Message> history = chatMemoryHelper.loadHistory(chatMemory, sessionId);
           String historyContext = chatMemoryHelper.buildHistoryContext(history);

           String answer;
           if (historyContext.isBlank()) {
               answer = chatClient.prompt()
                       .system("你是一个有帮助的AI助手，请用简洁专业的中文回答。")
                       .user(request.getMessage())
                       .call()
                       .content();
           } else {
               answer = chatClient.prompt()
                       .system("你是一个有帮助的AI助手。请结合以下历史对话继续回答，保持上下文一致。\n" + historyContext)
                       .user(request.getMessage())
                       .call()
                       .content();
           }

           // 显式追加 user + assistant，避免仅保存 assistant 的历史缺失问题
           chatMemoryHelper.appendRound(chatMemory, sessionId, request.getMessage(), answer);

           try {
               chatMessagePersistenceService.saveRoundMessages(sessionId, currentUserId, request.getMessage(), answer);
           } catch (Exception e) {
               // 可用性优先：落库失败不影响本次对话返回
               logger.error("消息持久化失败，已降级继续返回 - sessionId: {}, userId: {}", sessionId, currentUserId, e);
           }


           return ApiResponse.success(ChatResponse.success(answer,sessionId));
       }catch (SecurityException e){
           logger.warn("会话归属校验失败: {}", e.getMessage());
           return ApiResponse.error(e.getMessage());
       }catch (Exception e){
           logger.error("对话失败",e);
           return ApiResponse.success(ChatResponse.error("模型调用失败: " + e.getMessage(),request.getSessionId()));
       }
    }

    /**
     * 查询会话列表
     * @return
     */
    @Override
    public List<ChatSessionItem> listSessions() {
        // 当前用户来自 JWT 解析结果，用于会话归属校验
        Long currentUserId = UserContextHolder.getUserId();
        if (currentUserId == null) {
            throw new SecurityException("未登录或登录已过期");
        }
        List<ChatSessionMetaEntity> sessions =
                chatSessionMetaMapper.findByUserId(currentUserId);

        return sessions.stream().map(s -> {
            ChatSessionItem item = new ChatSessionItem();
            item.setSessionId(s.getSessionId());
            item.setTitle(s.getTitle());
            item.setLastMessageAt(s.getLastMessageAt());
            item.setUpdatedAt(s.getUpdatedAt());
            return item;
        }).toList();
    }

    /**
     * 查询历史对话列表
     * @param sessionId
     * @return
     */
    @Override
    public List<ChatMessageItem> listMessages(String sessionId) {
        Long currentUserId = UserContextHolder.getUserId();
        if (currentUserId == null) {
            throw new SecurityException("未登录或登录已过期");
        }
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId不能为空");
        }

        ChatSessionMetaEntity sessionMeta = chatSessionMetaMapper.findBySessionId(sessionId);
        if (sessionMeta == null) {
            throw new IllegalArgumentException("会话不存在");
        }
        if (!currentUserId.equals(sessionMeta.getUserId())) {
            throw new SecurityException("无权访问该会话");
        }

        return chatMessageMapper.findBySessionId(sessionId).stream().map(m -> {
            ChatMessageItem item = new ChatMessageItem();
            item.setId(m.getId());
            item.setSessionId(m.getSessionId());
            item.setRole(m.getRole());
            item.setContent(m.getContent());
            item.setCreatedAt(m.getCreatedAt());
            return item;
        }).toList();
    }

    private void upsertAndValidateSessionOwnership(String sessionId, Long userId, String message) {
        ChatSessionMetaEntity existing = chatSessionMetaMapper.findBySessionId(sessionId);
        if (existing == null) {
            // 新会话：建立 session 与当前 user 的绑定关系
            ChatSessionMetaEntity sessionMeta = new ChatSessionMetaEntity();
            sessionMeta.setSessionId(sessionId);
            sessionMeta.setUserId(userId);
            sessionMeta.setTitle(buildTitle(message));
            sessionMeta.setLastMessageAt(LocalDateTime.now());
            chatSessionMetaMapper.insert(sessionMeta);
            return;
        }

        // 老会话：归属不一致即拒绝访问（防止跨用户越权）
        if (!userId.equals(existing.getUserId())) {
            throw new SecurityException("无权访问该会话");
        }

        // 归属一致则更新会话元信息，便于后续会话列表排序
        existing.setTitle(buildTitle(message));
        existing.setLastMessageAt(LocalDateTime.now());
        chatSessionMetaMapper.updateSessionMeta(existing);
    }

    private String buildTitle(String message) {
        if (message == null || message.isBlank()) {
            return "新会话";
        }
        String trimmed = message.trim();
        return trimmed.length() <= 30 ? trimmed : trimmed.substring(0, 30);
    }
}
