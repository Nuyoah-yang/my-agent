package com.example.super_biz_agent.service;

import com.example.super_biz_agent.dto.auth.AuthResponse;
import com.example.super_biz_agent.dto.auth.LoginRequest;
import com.example.super_biz_agent.dto.auth.RegisterRequest;
import com.example.super_biz_agent.dto.auth.UserProfileResponse;

public interface AuthService {
    /**
     * 注册
     * @param request
     * @return
     */
    AuthResponse register(RegisterRequest request);

    /**
     * 登录
     * @param request
     * @return
     */
    AuthResponse login(LoginRequest request);

    UserProfileResponse getCurrentUserProfile(Long userId);
}
