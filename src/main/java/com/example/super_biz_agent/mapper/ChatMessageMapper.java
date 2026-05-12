package com.example.super_biz_agent.mapper;


import com.example.super_biz_agent.domain.ChatMessageEntity;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ChatMessageMapper {

    @Insert("INSERT INTO chat_message " +
            "(session_id, user_id, role, content) " +
            "VALUES " +
            "(#{sessionId}, #{userId}, #{role}, #{content})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ChatMessageEntity message);

    @Select("SELECT id, session_id, user_id, role, content, created_at " +
            "FROM chat_message " +
            "WHERE session_id = #{sessionId} " +
            "ORDER BY created_at ASC")
    List<ChatMessageEntity> findBySessionId(@Param("sessionId") String sessionId);
}
