package com.example.super_biz_agent.controller;

import com.example.super_biz_agent.dto.ApiResponse;
import com.example.super_biz_agent.dto.auth.AuthResponse;
import com.example.super_biz_agent.dto.auth.LoginRequest;
import com.example.super_biz_agent.dto.auth.RegisterRequest;
import com.example.super_biz_agent.dto.auth.UserProfileResponse;
import com.example.super_biz_agent.security.UserContextHolder;
import com.example.super_biz_agent.service.AuthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ApiResponse<AuthResponse> register(@RequestBody RegisterRequest request) {
        try {
            // 注册成功后直接返回 access token，前端可立即进入已登录态
            return ApiResponse.success(authService.register(request));
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@RequestBody LoginRequest request) {
        try {
            // 登录返回 token + 用户基础信息，便于前端初始化用户状态
            return ApiResponse.success(authService.login(request));
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @GetMapping("/me")
    public ApiResponse<UserProfileResponse> me() {
        try {
            // 用户身份从 JWT 过滤器注入，不依赖客户端传 userId
            Long userId = UserContextHolder.getUserId();
            return ApiResponse.success(authService.getCurrentUserProfile(userId));
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }
}
