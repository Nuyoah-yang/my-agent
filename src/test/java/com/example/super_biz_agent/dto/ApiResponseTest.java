package com.example.super_biz_agent.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ApiResponse")
class ApiResponseTest {

    @Nested
    @DisplayName("success")
    class Success {

        @Test
        @DisplayName("code is 200")
        void codeIs200() {
            ApiResponse<String> r = ApiResponse.success("hello");
            assertThat(r.getCode()).isEqualTo(200);
        }

        @Test
        @DisplayName("message is 'success'")
        void messageIsSuccess() {
            ApiResponse<String> r = ApiResponse.success("hello");
            assertThat(r.getMessage()).isEqualTo("success");
        }

        @Test
        @DisplayName("data is the passed object")
        void dataMatches() {
            ApiResponse<String> r = ApiResponse.success("hello");
            assertThat(r.getData()).isEqualTo("hello");
        }

        @Test
        @DisplayName("null data is allowed")
        void nullData() {
            ApiResponse<Object> r = ApiResponse.success(null);
            assertThat(r.getCode()).isEqualTo(200);
            assertThat(r.getData()).isNull();
        }

        @Test
        @DisplayName("generic type resolution — Integer")
        void genericInteger() {
            ApiResponse<Integer> r = ApiResponse.success(42);
            assertThat(r.getData()).isEqualTo(42);
        }
    }

    @Nested
    @DisplayName("error(String)")
    class ErrorWithMessage {

        @Test
        @DisplayName("default code is 500")
        void defaultCode500() {
            ApiResponse<Void> r = ApiResponse.error("fail");
            assertThat(r.getCode()).isEqualTo(500);
        }

        @Test
        @DisplayName("message matches input")
        void messageMatches() {
            ApiResponse<Void> r = ApiResponse.error("something broke");
            assertThat(r.getMessage()).isEqualTo("something broke");
        }

        @Test
        @DisplayName("data is null")
        void dataIsNull() {
            ApiResponse<Void> r = ApiResponse.error("fail");
            assertThat(r.getData()).isNull();
        }

        @Test
        @DisplayName("empty string message")
        void emptyMessage() {
            ApiResponse<Void> r = ApiResponse.error("");
            assertThat(r.getMessage()).isEmpty();
        }

        @Test
        @DisplayName("null message")
        void nullMessage() {
            ApiResponse<Void> r = ApiResponse.error(null);
            assertThat(r.getMessage()).isNull();
        }
    }

    @Nested
    @DisplayName("error(int, String)")
    class ErrorWithCode {

        @Test
        @DisplayName("custom code 404")
        void customCode404() {
            ApiResponse<Void> r = ApiResponse.error(404, "not found");
            assertThat(r.getCode()).isEqualTo(404);
        }

        @Test
        @DisplayName("custom code 401")
        void customCode401() {
            ApiResponse<Void> r = ApiResponse.error(401, "unauthorized");
            assertThat(r.getCode()).isEqualTo(401);
        }

        @Test
        @DisplayName("code 0")
        void codeZero() {
            ApiResponse<Void> r = ApiResponse.error(0, "zero");
            assertThat(r.getCode()).isZero();
        }

        @Test
        @DisplayName("null message with custom code")
        void nullMessageCustomCode() {
            ApiResponse<Void> r = ApiResponse.error(400, null);
            assertThat(r.getCode()).isEqualTo(400);
            assertThat(r.getMessage()).isNull();
        }
    }
}
