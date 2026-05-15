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
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.agent.Builder;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.example.super_biz_agent.agent.tool.RagSearchTool;
import com.example.super_biz_agent.config.RagConfigProperties;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.messages.Message;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;


@Service
public class ChatServiceImpl implements ChatService {
    private static final Logger logger = LoggerFactory.getLogger(ChatServiceImpl.class);

    private final ChatModel chatModel;
    private final RagSearchTool ragSearchTool;
    private final RagConfigProperties ragConfig;
    private final ChatMemoryRepository chatMemoryRepository;
    private final ChatMemoryHelper chatMemoryHelper;
    private final ChatMessageMapper chatMessageMapper;
    private final ChatSessionMetaMapper chatSessionMetaMapper;
    private final ChatMessagePersistenceService chatMessagePersistenceService;

    @Value("${chat.memory.max-messages:10}")
    private int maxMessages;
    @Value("${chat.prompt.base:你是一个有帮助的AI助手，请用简洁专业的中文回答。}")
    private String baseSystemPrompt;
    @Value("${chat.prompt.with-history:你是一个有帮助的AI助手。请结合以下历史对话继续回答，保持上下文一致。}")
    private String historySystemPrompt;

    @Value("${chat.prompt.base-with-rag:你是一个业务知识助手。你可以使用 queryInternalDocs 工具搜索知识库中的文档来获取信息。}")
    private String baseSystemPromptWithRag;

    public ChatServiceImpl(ChatModel chatModel,
                           RagSearchTool ragSearchTool,
                           RagConfigProperties ragConfig,
                           RedisChatMemoryRepository chatMemoryRepository,
                           ChatMemoryHelper chatMemoryHelper,
                           ChatMessageMapper chatMessageMapper,
                           ChatSessionMetaMapper chatSessionMetaMapper,
                           ChatMessagePersistenceService chatMessagePersistenceService
                           ) {
        this.chatModel = chatModel;
        this.ragSearchTool = ragSearchTool;
        this.ragConfig = ragConfig;
        this.chatMemoryRepository = chatMemoryRepository;
        this.chatMemoryHelper = chatMemoryHelper;
        this.chatMessageMapper = chatMessageMapper;
        this.chatSessionMetaMapper = chatSessionMetaMapper;
        this.chatMessagePersistenceService = chatMessagePersistenceService;
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

           ReactAgent agent = createAgent(buildSystemPrompt(historyContext));
           String answer = agent.call(request.getMessage()).getText();

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

    /**
     * 删除对应会话
     * @param sessionId
     */
    @Override
    public void deleteSession(String sessionId) {
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
            throw new SecurityException("无权删除该会话");
        }

        chatSessionMetaMapper.deleteBySessionId(sessionId);
        chatMemoryRepository.deleteByConversationId(sessionId);
    }

    /**
     * 重命名会话标题，仅允许会话归属用户操作。
     */
    @Override
    public void renameSession(String sessionId, String title) {
        Long currentUserId = UserContextHolder.getUserId();
        if (currentUserId == null) {
            throw new SecurityException("未登录或登录已过期");
        }
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId不能为空");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("标题不能为空");
        }

        String trimmedTitle = title.trim();
        if (trimmedTitle.length() > 128) {
            throw new IllegalArgumentException("标题长度不能超过128");
        }

        ChatSessionMetaEntity sessionMeta = chatSessionMetaMapper.findBySessionId(sessionId);
        if (sessionMeta == null) {
            throw new IllegalArgumentException("会话不存在");
        }
        if (!currentUserId.equals(sessionMeta.getUserId())) {
            throw new SecurityException("无权重命名该会话");
        }

        chatSessionMetaMapper.updateTitleBySessionId(sessionId, trimmedTitle);
    }

    /**
     * 流式传输
     * @param request
     * @return
     */
    @Override
    public SseEmitter chatStream(ChatRequest request) {
        // 5分钟超时，覆盖普通问答和中等长度流式输出。
        SseEmitter emitter = new SseEmitter(300000L);

        // 1) 前置参数和登录态校验
        if (request == null || request.getMessage() == null || request.getMessage().trim().isEmpty()) {
            sendSseError(emitter, "问题内容不能为空");
            emitter.complete();
            return emitter;
        }
        Long currentUserId = UserContextHolder.getUserId();
        if (currentUserId == null) {
            sendSseError(emitter, "未登录或登录已过期");
            emitter.complete();
            return emitter;
        }

        // 2) 复用已有会话归属逻辑，确保流式接口也具备同等安全边界
        String sessionId = chatMemoryHelper.normalizeSessionId(request.getSessionId());
        try {
            upsertAndValidateSessionOwnership(sessionId, currentUserId, request.getMessage());
        } catch (Exception e) {
            sendSseError(emitter, e.getMessage());
            emitter.complete();
            return emitter;
        }

        // 3) 构建聊天记忆上下文（与非流式 chat 接口一致）
        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(maxMessages)
                .build();
        List<Message> history = chatMemoryHelper.loadHistory(chatMemory, sessionId);
        String historyContext = chatMemoryHelper.buildHistoryContext(history);

        // 4) 订阅流式输出并累积完整答案，结束后统一写 memory + DB
        StringBuilder fullAnswerBuilder = new StringBuilder();
        Flux<NodeOutput> stream;
        try {
            ReactAgent agent = createAgent(buildSystemPrompt(historyContext));
            stream = agent.stream(request.getMessage());
        } catch (GraphRunnerException e) {
            logger.error("ReactAgent 流式启动失败 - sessionId: {}", sessionId, e);
            sendSseError(emitter, "代理引擎启动失败: " + e.getMessage());
            emitter.complete();
            return emitter;
        }

        final Disposable[] streamDisposable = new Disposable[1];
        streamDisposable[0] = stream.subscribe(
                output -> {
                    try {
                        if (output instanceof StreamingOutput streamingOutput) {
                            OutputType type = streamingOutput.getOutputType();

                            if (type == OutputType.AGENT_MODEL_STREAMING) {
                                String chunk = streamingOutput.message().getText();
                                if (chunk != null && !chunk.isEmpty()) {
                                    fullAnswerBuilder.append(chunk);
                                    sendSseContent(emitter, chunk);
                                }
                            } else if (type == OutputType.AGENT_MODEL_FINISHED) {
                                logger.debug("ReactAgent 模型推理完成");
                            } else if (type == OutputType.AGENT_TOOL_FINISHED) {
                                logger.info("ReactAgent 工具调用完成: {}", output.node());
                            }
                        }
                    } catch (Exception e) {
                        logger.warn("处理流式输出事件失败", e);
                    }
                },
                error -> {
                    logger.error("流式对话失败 - sessionId: {}", sessionId, error);
                    sendSseError(emitter, "模型流式调用失败: " + error.getMessage());
                    emitter.complete();
                },
                () -> {
                    String answer = fullAnswerBuilder.toString();
                    if (answer.isBlank()) {
                        sendSseError(emitter, "模型未返回有效内容");
                        emitter.complete();
                        return;
                    }

                    // 与非流式接口保持一致：先写记忆，再异步落库（可用性优先）
                    chatMemoryHelper.appendRound(chatMemory, sessionId, request.getMessage(), answer);
                    try {
                        chatMessagePersistenceService.saveRoundMessages(sessionId, currentUserId, request.getMessage(), answer);
                    } catch (Exception e) {
                        logger.error("流式消息持久化失败，已降级继续返回 - sessionId: {}, userId: {}", sessionId, currentUserId, e);
                    }

                    sendSseDone(emitter);
                    emitter.complete();
                }
        );

        // 5) 连接结束时释放订阅，避免资源泄露
        emitter.onCompletion(() -> {
            if (streamDisposable[0] != null && !streamDisposable[0].isDisposed()) {
                streamDisposable[0].dispose();
            }
        });
        emitter.onTimeout(() -> {
            if (streamDisposable[0] != null && !streamDisposable[0].isDisposed()) {
                streamDisposable[0].dispose();
            }
            sendSseError(emitter, "流式响应超时");
            emitter.complete();
        });

        return emitter;
    }

    /**
     * SSE content 事件：前端会将 data 拼接成实时回答。
     */
    private void sendSseContent(SseEmitter emitter, String chunk) {
        sendSseMessage(emitter, "content", chunk);
    }

    /**
     * SSE done 事件：标记流式输出结束。
     */
    private void sendSseDone(SseEmitter emitter) {
        sendSseMessage(emitter, "done", null);
    }

    /**
     * SSE error 事件：前端可按统一协议展示错误提示。
     */
    private void sendSseError(SseEmitter emitter, String errorMessage) {
        sendSseMessage(emitter, "error", errorMessage == null ? "未知错误" : errorMessage);
    }

    /**
     * 统一 SSE 消息格式：{"type":"content|done|error","data":...}
     */
    private void sendSseMessage(SseEmitter emitter, String type, String data) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("type", type);
            payload.put("data", data);
            emitter.send(SseEmitter.event().name("message").data(payload, MediaType.APPLICATION_JSON));
        } catch (Exception e) {
            logger.warn("SSE消息发送失败 - type: {}, error: {}", type, e.getMessage());
        }
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

        // 归属一致则更新会话元信息。
        // 仅在标题为空或仍为默认值时才自动生成标题，避免覆盖用户手动重命名结果。
        if (existing.getTitle() == null || existing.getTitle().isBlank() || "新会话".equals(existing.getTitle())) {
            existing.setTitle(buildTitle(message));
        }
        existing.setLastMessageAt(LocalDateTime.now());
        chatSessionMetaMapper.updateSessionMeta(existing);
    }

    /**
     * Create a ReactAgent for the current request.
     * When RAG is disabled, no tools are registered (agent behaves like plain ChatModel).
     */
    private ReactAgent createAgent(String systemPrompt) {
        Builder builder = ReactAgent.builder()
                .name("super_biz_agent")
                .model(chatModel)
                .systemPrompt(systemPrompt);

        if (ragConfig.isEnabled()) {
            builder.methodTools(new Object[]{ragSearchTool});
        }

        return builder.build();
    }

    /**
     * 统一构建系统提示词，根据 RAG 开关选择 base prompt。
     */
    private String buildSystemPrompt(String historyContext) {
        String basePrompt = ragConfig.isEnabled() ? baseSystemPromptWithRag : baseSystemPrompt;

        if (historyContext == null || historyContext.isBlank()) {
            return basePrompt;
        }
        return basePrompt + "\n\n--- 历史对话 ---\n" + historyContext + "\n--- 历史对话结束 ---\n请基于以上历史对话继续回答。";
    }

    private String buildTitle(String message) {
        if (message == null || message.isBlank()) {
            return "新会话";
        }
        String trimmed = message.trim();
        return trimmed.length() <= 30 ? trimmed : trimmed.substring(0, 30);
    }
}
