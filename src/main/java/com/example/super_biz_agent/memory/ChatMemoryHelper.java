package com.example.super_biz_agent.memory;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class ChatMemoryHelper {

    public String normalizeSessionId(String sessionId) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            return UUID.randomUUID().toString();
        }
        return sessionId.trim();
    }

    public List<Message> loadHistory(ChatMemory chatMemory, String sessionId) {
        return chatMemory.get(sessionId);
    }

    public void appendRound(ChatMemory chatMemory, String sessionId, String userText, String assistantText) {
        chatMemory.add(sessionId, new UserMessage(userText));
        chatMemory.add(sessionId, new AssistantMessage(assistantText));
    }

    public String buildHistoryContext(List<Message> history) {
        if (history == null || history.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Message message : history) {
            String text = message.getText();
            if (text == null || text.isBlank()) {
                continue;
            }
            MessageType type = message.getMessageType();
            if (type == MessageType.USER) {
                sb.append("user: ").append(text).append("\n");
            } else if (type == MessageType.ASSISTANT) {
                sb.append("assistant: ").append(text).append("\n");
            }
        }
        return sb.toString().trim();
    }
}
