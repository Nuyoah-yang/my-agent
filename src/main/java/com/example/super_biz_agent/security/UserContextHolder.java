package com.example.super_biz_agent.security;

public final class UserContextHolder {

    //线程局部变量，存储当前请求的用户ID
    private static final ThreadLocal<Long> USER_ID_HOLDER = new ThreadLocal<>();
    //私有构造器，防止实例化
    private UserContextHolder() {
    }
    //设置当前线程的用户ID
    public static void setUserId(Long userId) {
        USER_ID_HOLDER.set(userId);
    }
    //获取当前线程的用户ID
    public static Long getUserId() {
        return USER_ID_HOLDER.get();
    }
    //清除当前线程的用户ID（防止内存泄漏）
    public static void clear() {
        USER_ID_HOLDER.remove();
    }
}
