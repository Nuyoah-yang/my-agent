package com.example.super_biz_agent.exception;

import com.example.super_biz_agent.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("GlobalExceptionHandler")
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    // ==================== isSseRequest ====================

    @Nested
    @DisplayName("isSseRequest")
    class IsSseRequest {

        @Test
        @DisplayName("Accept header contains text/event-stream → true")
        void acceptContainsSse() throws Exception {
            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getHeader("Accept")).thenReturn("text/event-stream");
            boolean result = invokeIsSseRequest(request);
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("Accept header is text/event-stream exactly → true")
        void acceptExactSse() throws Exception {
            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getHeader("Accept")).thenReturn(MediaType.TEXT_EVENT_STREAM_VALUE);
            boolean result = invokeIsSseRequest(request);
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("Accept header is application/json → false")
        void acceptJson() throws Exception {
            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getHeader("Accept")).thenReturn(MediaType.APPLICATION_JSON_VALUE);
            boolean result = invokeIsSseRequest(request);
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("Accept header is null → false (no NPE)")
        void acceptNull() throws Exception {
            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getHeader("Accept")).thenReturn(null);
            boolean result = invokeIsSseRequest(request);
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("mixed Accept header containing text/event-stream → true")
        void acceptMixed() throws Exception {
            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getHeader("Accept")).thenReturn("application/json, text/event-stream");
            boolean result = invokeIsSseRequest(request);
            assertThat(result).isTrue();
        }
    }

    // ==================== buildSseError ====================

    @Nested
    @DisplayName("buildSseError")
    class BuildSseError {

        @Test
        @DisplayName("returns ResponseEntity with correct status")
        void correctStatus() throws Exception {
            ResponseEntity<String> response = invokeBuildSseError(HttpStatus.UNAUTHORIZED, "unauthorized");
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("Content-Type is text/event-stream")
        void contentTypeSse() throws Exception {
            ResponseEntity<String> response = invokeBuildSseError(HttpStatus.BAD_REQUEST, "bad");
            assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.TEXT_EVENT_STREAM);
        }

        @Test
        @DisplayName("body contains SSE format with type:error")
        void bodyContainsSseFormat() throws Exception {
            ResponseEntity<String> response = invokeBuildSseError(HttpStatus.INTERNAL_SERVER_ERROR, "test error");
            String body = response.getBody();
            assertThat(body).startsWith("data: ");
            assertThat(body).contains("\"type\":\"error\"");
            assertThat(body).contains("test error");
            assertThat(body).endsWith("\n\n");
        }

        @Test
        @DisplayName("null message → uses '未知错误'")
        void nullMessage() throws Exception {
            ResponseEntity<String> response = invokeBuildSseError(HttpStatus.INTERNAL_SERVER_ERROR, null);
            assertThat(response.getBody()).contains("未知错误");
        }

        @Test
        @DisplayName("message with double quote → escaped")
        void doubleQuoteEscaped() throws Exception {
            ResponseEntity<String> response = invokeBuildSseError(HttpStatus.BAD_REQUEST, "he said \"hello\"");
            assertThat(response.getBody()).contains("he said \\\"hello\\\"");
        }

        @Test
        @DisplayName("500 status")
        void status500() throws Exception {
            ResponseEntity<String> response = invokeBuildSseError(HttpStatus.INTERNAL_SERVER_ERROR, "fail");
            assertThat(response.getStatusCodeValue()).isEqualTo(500);
        }

        @Test
        @DisplayName("401 status")
        void status401() throws Exception {
            ResponseEntity<String> response = invokeBuildSseError(HttpStatus.UNAUTHORIZED, "auth fail");
            assertThat(response.getStatusCodeValue()).isEqualTo(401);
        }

        @Test
        @DisplayName("415 status")
        void status415() throws Exception {
            ResponseEntity<String> response = invokeBuildSseError(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "bad media");
            assertThat(response.getStatusCodeValue()).isEqualTo(415);
        }
    }

    // ==================== Exception Handler Methods ====================

    @Nested
    @DisplayName("handleSecurityException")
    class HandleSecurityException {

        @Test
        @DisplayName("non-SSE request → 401 + ApiResponse")
        void nonSse() {
            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getHeader("Accept")).thenReturn(MediaType.APPLICATION_JSON_VALUE);

            ResponseEntity<?> response = handler.handleSecurityException(
                    new SecurityException("请先登录"), request);

            assertThat(response.getStatusCodeValue()).isEqualTo(401);
            Object body = response.getBody();
            assertThat(body).isInstanceOf(ApiResponse.class);
            ApiResponse<?> api = (ApiResponse<?>) body;
            assertThat(api.getCode()).isEqualTo(401);
            assertThat(api.getMessage()).isEqualTo("请先登录");
        }

        @Test
        @DisplayName("SSE request → 401 + SSE body")
        void sse() {
            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getHeader("Accept")).thenReturn(MediaType.TEXT_EVENT_STREAM_VALUE);

            ResponseEntity<?> response = handler.handleSecurityException(
                    new SecurityException("token expired"), request);

            assertThat(response.getStatusCodeValue()).isEqualTo(401);
            assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.TEXT_EVENT_STREAM);
            assertThat((String) response.getBody()).contains("token expired");
        }
    }

    @Nested
    @DisplayName("handleIllegalArgumentException")
    class HandleIllegalArgumentException {

        @Test
        @DisplayName("non-SSE request → 400 + ApiResponse")
        void nonSse() {
            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getHeader("Accept")).thenReturn(MediaType.APPLICATION_JSON_VALUE);

            ResponseEntity<?> response = handler.handleIllegalArgumentException(
                    new IllegalArgumentException("参数不合法"), request);

            assertThat(response.getStatusCodeValue()).isEqualTo(400);
            ApiResponse<?> api = (ApiResponse<?>) response.getBody();
            assertThat(api.getCode()).isEqualTo(400);
            assertThat(api.getMessage()).isEqualTo("参数不合法");
        }

        @Test
        @DisplayName("SSE request → 400 + SSE body")
        void sse() {
            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getHeader("Accept")).thenReturn(MediaType.TEXT_EVENT_STREAM_VALUE);

            ResponseEntity<?> response = handler.handleIllegalArgumentException(
                    new IllegalArgumentException("bad argument"), request);

            assertThat(response.getStatusCodeValue()).isEqualTo(400);
            assertThat((String) response.getBody()).contains("\"type\":\"error\"");
        }
    }

    @Nested
    @DisplayName("handleMediaTypeNotSupported")
    class HandleMediaTypeNotSupported {

        @Test
        @DisplayName("non-SSE request → 415 + ApiResponse")
        void nonSse() {
            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getHeader("Accept")).thenReturn(MediaType.APPLICATION_JSON_VALUE);

            ResponseEntity<?> response = handler.handleMediaTypeNotSupported(
                    new HttpMediaTypeNotSupportedException("bad"), request);

            assertThat(response.getStatusCodeValue()).isEqualTo(415);
            ApiResponse<?> api = (ApiResponse<?>) response.getBody();
            assertThat(api.getCode()).isEqualTo(415);
            assertThat(api.getMessage()).contains("Content-Type");
        }

        @Test
        @DisplayName("SSE request → 415 + SSE body")
        void sse() {
            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getHeader("Accept")).thenReturn(MediaType.TEXT_EVENT_STREAM_VALUE);

            ResponseEntity<?> response = handler.handleMediaTypeNotSupported(
                    new HttpMediaTypeNotSupportedException("bad"), request);

            assertThat(response.getStatusCodeValue()).isEqualTo(415);
            assertThat((String) response.getBody()).contains("\"type\":\"error\"");
        }
    }

    @Nested
    @DisplayName("handleException (fallback)")
    class HandleExceptionFallback {

        @Test
        @DisplayName("non-SSE request → 500 + 服务器内部错误")
        void nonSse() {
            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getHeader("Accept")).thenReturn(MediaType.APPLICATION_JSON_VALUE);

            ResponseEntity<?> response = handler.handleException(
                    new RuntimeException("internal"), request);

            assertThat(response.getStatusCodeValue()).isEqualTo(500);
            ApiResponse<?> api = (ApiResponse<?>) response.getBody();
            assertThat(api.getCode()).isEqualTo(500);
            assertThat(api.getMessage()).isEqualTo("服务器内部错误");
        }

        @Test
        @DisplayName("SSE request → 500 + SSE body")
        void sse() {
            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getHeader("Accept")).thenReturn(MediaType.TEXT_EVENT_STREAM_VALUE);

            ResponseEntity<?> response = handler.handleException(
                    new RuntimeException("boom"), request);

            assertThat(response.getStatusCodeValue()).isEqualTo(500);
            assertThat((String) response.getBody()).contains("\"type\":\"error\"");
        }
    }

    // ==================== reflection helpers ====================

    private boolean invokeIsSseRequest(HttpServletRequest request) throws Exception {
        var method = GlobalExceptionHandler.class.getDeclaredMethod("isSseRequest", HttpServletRequest.class);
        method.setAccessible(true);
        return (boolean) method.invoke(handler, request);
    }

    @SuppressWarnings("unchecked")
    private ResponseEntity<String> invokeBuildSseError(HttpStatus status, String message) throws Exception {
        var method = GlobalExceptionHandler.class.getDeclaredMethod("buildSseError", HttpStatus.class, String.class);
        method.setAccessible(true);
        return (ResponseEntity<String>) method.invoke(handler, status, message);
    }
}
