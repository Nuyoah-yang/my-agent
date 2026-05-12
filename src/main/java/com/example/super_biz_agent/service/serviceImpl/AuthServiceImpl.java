package com.example.super_biz_agent.service.serviceImpl;

import com.example.super_biz_agent.domain.UserAuthEntity;
import com.example.super_biz_agent.domain.UserEntity;
import com.example.super_biz_agent.dto.auth.AuthResponse;
import com.example.super_biz_agent.dto.auth.LoginRequest;
import com.example.super_biz_agent.dto.auth.RegisterRequest;
import com.example.super_biz_agent.dto.auth.UserProfileResponse;
import com.example.super_biz_agent.mapper.UserAuthMapper;
import com.example.super_biz_agent.mapper.UserMapper;
import com.example.super_biz_agent.security.JwtService;
import com.example.super_biz_agent.service.AuthService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final UserAuthMapper userAuthMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthServiceImpl(UserMapper userMapper,
                           UserAuthMapper userAuthMapper,
                           PasswordEncoder passwordEncoder,
                           JwtService jwtService) {
        this.userMapper = userMapper;
        this.userAuthMapper = userAuthMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AuthResponse register(RegisterRequest request) {
        // 注册流程：参数校验 -> 用户写入 -> 密码哈希写入 -> 直接签发 token
        validateRegisterRequest(request);
        UserEntity existing = userMapper.findByUsername(request.getUsername());
        if (existing != null) {
            throw new IllegalArgumentException("用户名已存在");
        }

        UserEntity user = new UserEntity();
        user.setUsername(request.getUsername().trim());
        user.setNickname(resolveNickname(request));
        user.setAvatarUrl(request.getAvatarUrl());
        user.setStatus(1);
        userMapper.insert(user);

        UserAuthEntity userAuth = new UserAuthEntity();
        userAuth.setUserId(user.getId());
        userAuth.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        userAuth.setPasswordSalt(null);
        userAuth.setLoginFailCount(0);
        userAuthMapper.insert(userAuth);

        String token = jwtService.generateAccessToken(user.getId(), user.getUsername());
        return new AuthResponse(
                token,
                jwtService.getAccessTokenExpireSeconds(),
                user.getId(),
                user.getUsername(),
                user.getNickname()
        );
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        // 登录流程：查用户 -> 校验状态 -> 比对密码 -> 更新登录状态 -> 签发 token
        if (request == null || isBlank(request.getUsername()) || isBlank(request.getPassword())) {
            throw new IllegalArgumentException("用户名和密码不能为空");
        }
        UserEntity user = userMapper.findByUsername(request.getUsername().trim());
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            throw new IllegalArgumentException("用户不存在或已禁用");
        }
        UserAuthEntity userAuth = userAuthMapper.findByUserId(user.getId());
        if (userAuth == null || !passwordEncoder.matches(request.getPassword(), userAuth.getPasswordHash())) {
            // 失败次数先留痕，后续可扩展锁定策略
            if (userAuth != null) {
                userAuthMapper.increaseLoginFailCount(user.getId());
            }
            throw new IllegalArgumentException("用户名或密码错误");
        }

        userAuthMapper.markLoginSuccess(user.getId());
        String token = jwtService.generateAccessToken(user.getId(), user.getUsername());
        return new AuthResponse(
                token,
                jwtService.getAccessTokenExpireSeconds(),
                user.getId(),
                user.getUsername(),
                user.getNickname()
        );
    }

    @Override
    public UserProfileResponse getCurrentUserProfile(Long userId) {
        // /me 只依赖 token 解析出的 userId，不接受前端传参篡改
        if (userId == null) {
            throw new IllegalArgumentException("未登录");
        }
        UserEntity user = userMapper.findById(userId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        return new UserProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getAvatarUrl(),
                user.getStatus()
        );
    }

    private void validateRegisterRequest(RegisterRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        if (isBlank(request.getUsername())) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        if (isBlank(request.getPassword()) || request.getPassword().length() < 6) {
            throw new IllegalArgumentException("密码长度不能小于6位");
        }
    }

    //实现昵称的降级策略，确保每个用户都有显示名称
    private String resolveNickname(RegisterRequest request) {
        if (!isBlank(request.getNickname())) {
            return request.getNickname().trim();
        }
        return request.getUsername().trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
