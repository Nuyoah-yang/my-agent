package com.example.super_biz_agent.exception;

import com.example.super_biz_agent.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.HttpMediaTypeNotSupportedException;

/**
 * 全局异常处理器：
 * 统一将控制层/服务层抛出的异常转换为 ApiResponse，避免每个 Controller 重复 try-catch。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 未登录、越权等安全问题统一返回 401。
     */
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<?> handleSecurityException(SecurityException e, HttpServletRequest request) {
        // SSE 请求必须返回 text/event-stream，否则会触发媒体类型不匹配异常。
        if (isSseRequest(request)) {
            return buildSseError(HttpStatus.UNAUTHORIZED, e.getMessage());
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(401, e.getMessage()));
    }

    /**
     * 参数校验或业务前置条件不满足统一返回 400。
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> handleIllegalArgumentException(IllegalArgumentException e, HttpServletRequest request) {
        if (isSseRequest(request)) {
            return buildSseError(HttpStatus.BAD_REQUEST, e.getMessage());
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(400, e.getMessage()));
    }

    /**
     * 请求 Content-Type 不匹配时返回明确提示，方便前后端联调。
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<?> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException e,
                                                         HttpServletRequest request) {
        String message = "请求 Content-Type 不支持。/api/chat 与 /api/chat/chat_stream 请使用 application/json；"
                + "/api/upload 请使用 multipart/form-data。";
        if (isSseRequest(request)) {
            return buildSseError(HttpStatus.UNSUPPORTED_MEDIA_TYPE, message);
        }
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ApiResponse.error(415, message));
    }

    /**
     * 兜底异常，避免异常堆栈直接暴露给前端。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleException(Exception e, HttpServletRequest request) {
        if (isSseRequest(request)) {
            return buildSseError(HttpStatus.INTERNAL_SERVER_ERROR, "服务器内部错误");
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(500, "服务器内部错误"));
    }

    /**
     * 判断当前请求是否是 SSE 场景。
     */
    private boolean isSseRequest(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        return accept != null && accept.contains(MediaType.TEXT_EVENT_STREAM_VALUE);
    }

    /**
     * 将异常统一包装成 SSE error 事件，兼容前端 readSseStream 解析协议。
     */
    private ResponseEntity<String> buildSseError(HttpStatus status, String message) {
        String safeMessage = message == null ? "未知错误" : message.replace("\"", "\\\"");
        String payload = "data: {\"type\":\"error\",\"data\":\"" + safeMessage + "\"}\n\n";
        return ResponseEntity.status(status)
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(payload);
    }
}
