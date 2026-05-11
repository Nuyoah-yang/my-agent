package com.example.super_biz_agent.dto.auth;

import lombok.Data;


@Data
public class RegisterRequest {
    //用户名（登录账号）
    private String username;
    //密码
    private String password;
    //用户昵称（显示名称）
    private String nickname;
    //头像URL
    private String avatarUrl;
}
