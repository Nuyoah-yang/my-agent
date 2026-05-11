package com.example.super_biz_agent.mapper;

import com.example.super_biz_agent.domain.UserEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper {

    // 按用户名查询，用于注册重复校验与登录查找
    @Select("SELECT id, username, nickname, avatar_url, status, created_at, updated_at " +
            "FROM users WHERE username = #{username} LIMIT 1")
    UserEntity findByUsername(String username);

    // 按用户ID查询，用于 /me 等场景
    @Select("SELECT id, username, nickname, avatar_url, status, created_at, updated_at " +
            "FROM users WHERE id = #{id} LIMIT 1")
    UserEntity findById(Long id);

    // 插入用户主数据，主键回填到 user.id
    @Insert("INSERT INTO users (username, nickname, avatar_url, status) " +
            "VALUES (#{username}, #{nickname}, #{avatarUrl}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(UserEntity user);
}
