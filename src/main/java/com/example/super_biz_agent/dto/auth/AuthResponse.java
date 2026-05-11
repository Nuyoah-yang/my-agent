package com.example.super_biz_agent.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    //访问令牌（JWT）
    private String accessToken;
    //令牌过期时间（秒）
    private Long expiresInSeconds;
    //用户ID
    private Long userId;
    //用户名（登录账号）
    private String username;
    //用户昵称（显示名称）
    private String nickname;
}
