package com.example.super_biz_agent.domain;
import java.time.LocalDateTime;

import lombok.Data;


@Data
public class UserAuthEntity {
    //用户ID（关联users表）
    private Long userId;
    //密码哈希值（BCrypt加密）
    private String passwordHash;
    //密码盐值（BCrypt不需要，保留字段兼容）
    private String passwordSalt;
    //最后登录时间
    private LocalDateTime lastLoginAt;
    //登录失败次数
    private Integer loginFailCount;
    //更新时间
    private LocalDateTime updatedAt;
}
