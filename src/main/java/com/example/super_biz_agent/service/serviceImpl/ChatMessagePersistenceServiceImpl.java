package com.example.super_biz_agent.service.serviceImpl;

import com.example.super_biz_agent.domain.ChatMessageEntity;
import com.example.super_biz_agent.domain.ChatMessageRole;
import com.example.super_biz_agent.mapper.ChatMessageMapper;
import com.example.super_biz_agent.service.ChatMessagePersistenceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(rollbackFor = Exception.class)
public class ChatMessagePersistenceServiceImpl implements ChatMessagePersistenceService {
    private final ChatMessageMapper chatMessageMapper;

    public ChatMessagePersistenceServiceImpl(ChatMessageMapper chatMessageMapper){
        this.chatMessageMapper=chatMessageMapper;
    }

    @Override
    public void saveRoundMessages(String sessionId, Long userId, String question, String answer) {
        ChatMessageEntity userMsg = ChatMessageEntity.builder()
                .sessionId(sessionId)
                .userId(userId)
                .role(ChatMessageRole.USER.getCode())
                .content(question)
                .build();
        ChatMessageEntity assistantMsg = ChatMessageEntity.builder()
                .sessionId(sessionId)
                .userId(userId)
                .role(ChatMessageRole.ASSISTANT.getCode())
                .content(answer)
                .build();
        chatMessageMapper.insert(userMsg);
        chatMessageMapper.insert(assistantMsg);
    }
}
