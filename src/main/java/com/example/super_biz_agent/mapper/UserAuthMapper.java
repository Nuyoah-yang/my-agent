package com.example.super_biz_agent.mapper;

import com.example.super_biz_agent.domain.UserAuthEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserAuthMapper {

    // 认证信息按 userId 一对一查询
    @Select("SELECT user_id, password_hash, password_salt, last_login_at, login_fail_count, updated_at " +
            "FROM user_auth WHERE user_id = #{userId} LIMIT 1")
    UserAuthEntity findByUserId(Long userId);

    // 首次注册写入密码哈希
    @Insert("INSERT INTO user_auth (user_id, password_hash, password_salt, login_fail_count) " +
            "VALUES (#{userId}, #{passwordHash}, #{passwordSalt}, #{loginFailCount})")
    int insert(UserAuthEntity userAuth);

    // 登录成功：更新时间并重置失败计数
    @Update("UPDATE user_auth SET last_login_at = NOW(), login_fail_count = 0 WHERE user_id = #{userId}")
    int markLoginSuccess(Long userId);

    // 登录失败：累计失败次数，后续可扩展风控策略
    @Update("UPDATE user_auth SET login_fail_count = login_fail_count + 1 WHERE user_id = #{userId}")
    int increaseLoginFailCount(Long userId);
}
