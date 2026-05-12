package com.example.super_biz_agent.memory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
public class RedisChatMemoryRepository implements ChatMemoryRepository {

    private static final String KEY_PREFIX = "chat:session:";
    private static final String KEY_PATTERN = KEY_PREFIX + "*";

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration ttl = Duration.ofMinutes(30);

    public RedisChatMemoryRepository(RedisTemplate<String, String> redisTemplate,
                                     ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    //查询所有会话 ID
    @Override
    public List<String> findConversationIds() {
        Set<String> keys = redisTemplate.keys(KEY_PATTERN);
        if (keys == null || keys.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> ids = new ArrayList<>(keys.size());
        for (String key : keys) {
            ids.add(key.substring(KEY_PREFIX.length()));
        }
        return ids;
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        try {
            String json = redisTemplate.opsForValue().get(KEY_PREFIX + conversationId);
            if (json == null || json.isBlank()) {
                return new ArrayList<>();
            }
            List<StoredMessage> storedMessages =
                    objectMapper.readValue(json, new TypeReference<List<StoredMessage>>() {});
            List<Message> result = new ArrayList<>(storedMessages.size());
            for (StoredMessage stored : storedMessages) {
                if (stored.text == null || stored.text.isBlank()) {
                    continue;
                }
                if ("USER".equalsIgnoreCase(stored.messageType)) {
                    result.add(new UserMessage(stored.text));
                } else if ("SYSTEM".equalsIgnoreCase(stored.messageType)) {
                    result.add(new SystemMessage(stored.text));
                } else {
                    result.add(new AssistantMessage(stored.text));
                }
            }
            return result;
        } catch (Exception e) {
            // 反序列化失败时返回空，避免中断主流程；同时打印日志便于定位问题
            log.error("RedisChatMemoryRepository findByConversationId deserialize failed: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        try {
            List<StoredMessage> storedMessages = new ArrayList<>(messages.size());
            for (Message message : messages) {
                MessageType messageType = message.getMessageType();
                storedMessages.add(new StoredMessage(messageType.name(), message.getText()));
            }
            String json = objectMapper.writeValueAsString(storedMessages);
            redisTemplate.opsForValue().set(KEY_PREFIX + conversationId, json, ttl);
        } catch (Exception e) {
            System.err.println("RedisChatMemoryRepository saveAll serialize failed: " + e.getMessage());
        }
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        redisTemplate.delete(KEY_PREFIX + conversationId);
    }

    private static class StoredMessage {
        public String messageType;
        public String text;

        public StoredMessage() {
        }

        public StoredMessage(String messageType, String text) {
            this.messageType = messageType;
            this.text = text;
        }
    }
}
