package com.example.super_biz_agent.mapper;

import com.example.super_biz_agent.domain.ChatSessionMetaEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ChatSessionMetaMapper {

    // 按 sessionId 查询会话归属信息
    @Select("SELECT session_id, user_id, title, last_message_at, created_at, updated_at " +
            "FROM chat_session_meta WHERE session_id = #{sessionId} LIMIT 1")
    ChatSessionMetaEntity findBySessionId(String sessionId);

    // 新建会话元数据（首次创建 session 时落库）
    @Insert("INSERT INTO chat_session_meta (session_id, user_id, title, last_message_at) " +
            "VALUES (#{sessionId}, #{userId}, #{title}, #{lastMessageAt})")
    int insert(ChatSessionMetaEntity sessionMeta);

    // 更新会话标题和最后消息时间，支持会话列表排序与展示
    @Update("UPDATE chat_session_meta SET title = #{title}, last_message_at = #{lastMessageAt}, updated_at = NOW() " +
            "WHERE session_id = #{sessionId}")
    int updateSessionMeta(ChatSessionMetaEntity sessionMeta);
}
