package com.example.super_biz_agent.dto;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public  class ApiResponse <T>{
    private int code;
    private String message;
    private T data;

    public static <T> ApiResponse <T> success(T data) {
        ApiResponse<T> response=new ApiResponse<>();
        response.setCode(200);
        response.setMessage("success");
        response.setData(data);
        return response;
    }

    public static <T> ApiResponse<T> error(String message) {
        return error(500, message);
    }

    /**
     * 支持按异常类型返回自定义错误码，便于前后端约定统一的错误语义。
     */
    public static <T> ApiResponse<T> error(int code, String message) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setCode(code);
        response.setMessage(message);
        return response;
    }
}
