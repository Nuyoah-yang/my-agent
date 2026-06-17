package com.example.super_biz_agent.service.serviceImpl;

import com.example.super_biz_agent.dto.auth.RegisterRequest;
import com.example.super_biz_agent.mapper.UserAuthMapper;
import com.example.super_biz_agent.mapper.UserMapper;
import com.example.super_biz_agent.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

@DisplayName("AuthServiceImpl — private pure-logic methods")
class AuthServiceImplTest {

    private AuthServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AuthServiceImpl(
                mock(UserMapper.class),
                mock(UserAuthMapper.class),
                mock(PasswordEncoder.class),
                mock(JwtService.class)
        );
    }

    // ==================== isBlank ====================

    @Nested
    @DisplayName("isBlank")
    class IsBlank {

        @Test
        @DisplayName("null → true")
        void nullValue() throws Exception {
            assertThat((boolean) invokeIsBlank(null)).isTrue();
        }

        @Test
        @DisplayName("empty string → true")
        void emptyString() throws Exception {
            assertThat((boolean) invokeIsBlank("")).isTrue();
        }

        @Test
        @DisplayName("spaces only → true")
        void spacesOnly() throws Exception {
            assertThat((boolean) invokeIsBlank("   ")).isTrue();
        }

        @Test
        @DisplayName("non-blank → false")
        void nonBlank() throws Exception {
            assertThat((boolean) invokeIsBlank("hello")).isFalse();
        }

        @Test
        @DisplayName("string with leading/trailing spaces → false")
        void leadingTrailingSpaces() throws Exception {
            assertThat((boolean) invokeIsBlank("  a  ")).isFalse();
        }
    }

    // ==================== resolveNickname ====================

    @Nested
    @DisplayName("resolveNickname")
    class ResolveNickname {

        @Test
        @DisplayName("nickname present → returns trimmed nickname")
        void nicknamePresent() throws Exception {
            RegisterRequest req = new RegisterRequest();
            req.setUsername("john");
            req.setPassword("pass123");
            req.setNickname("  Johnny  ");

            String result = (String) invokeResolveNickname(req);
            assertThat(result).isEqualTo("Johnny");
        }

        @Test
        @DisplayName("nickname null → falls back to username")
        void nicknameNull() throws Exception {
            RegisterRequest req = new RegisterRequest();
            req.setUsername("john");
            req.setPassword("pass123");
            req.setNickname(null);

            String result = (String) invokeResolveNickname(req);
            assertThat(result).isEqualTo("john");
        }

        @Test
        @DisplayName("nickname empty → falls back to username")
        void nicknameEmpty() throws Exception {
            RegisterRequest req = new RegisterRequest();
            req.setUsername("john");
            req.setPassword("pass123");
            req.setNickname("");

            String result = (String) invokeResolveNickname(req);
            assertThat(result).isEqualTo("john");
        }

        @Test
        @DisplayName("nickname blank → falls back to username")
        void nicknameBlank() throws Exception {
            RegisterRequest req = new RegisterRequest();
            req.setUsername("john");
            req.setPassword("pass123");
            req.setNickname("   ");

            String result = (String) invokeResolveNickname(req);
            assertThat(result).isEqualTo("john");
        }
    }

    // ==================== validateRegisterRequest ====================

    @Nested
    @DisplayName("validateRegisterRequest")
    class ValidateRegisterRequest {

        @Test
        @DisplayName("null request → IllegalArgumentException")
        void nullRequest() {
            assertThatThrownBy(() -> invokeValidateRegisterRequest(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("请求体不能为空");
        }

        @Test
        @DisplayName("null username → IllegalArgumentException")
        void nullUsername() {
            RegisterRequest req = new RegisterRequest();
            req.setPassword("pass1234");
            assertThatThrownBy(() -> invokeValidateRegisterRequest(req))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("用户名不能为空");
        }

        @Test
        @DisplayName("empty username → IllegalArgumentException")
        void emptyUsername() {
            RegisterRequest req = new RegisterRequest();
            req.setUsername("");
            req.setPassword("pass1234");
            assertThatThrownBy(() -> invokeValidateRegisterRequest(req))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("用户名不能为空");
        }

        @Test
        @DisplayName("blank username → IllegalArgumentException")
        void blankUsername() {
            RegisterRequest req = new RegisterRequest();
            req.setUsername("   ");
            req.setPassword("pass1234");
            assertThatThrownBy(() -> invokeValidateRegisterRequest(req))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("用户名不能为空");
        }

        @Test
        @DisplayName("null password → IllegalArgumentException")
        void nullPassword() {
            RegisterRequest req = new RegisterRequest();
            req.setUsername("john");
            assertThatThrownBy(() -> invokeValidateRegisterRequest(req))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("密码长度不能小于6位");
        }

        @Test
        @DisplayName("5-char password → IllegalArgumentException")
        void passwordTooShort() {
            RegisterRequest req = new RegisterRequest();
            req.setUsername("john");
            req.setPassword("12345");
            assertThatThrownBy(() -> invokeValidateRegisterRequest(req))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("密码长度不能小于6位");
        }

        @Test
        @DisplayName("6-char password → no exception")
        void passwordExactly6() throws Exception {
            RegisterRequest req = new RegisterRequest();
            req.setUsername("john");
            req.setPassword("123456");
            invokeValidateRegisterRequest(req); // should not throw
        }

        @Test
        @DisplayName("valid request → no exception")
        void validRequest() throws Exception {
            RegisterRequest req = new RegisterRequest();
            req.setUsername("john.doe");
            req.setPassword("securePass123");
            req.setNickname("John");
            invokeValidateRegisterRequest(req); // should not throw
        }
    }

    // ==================== reflection helpers ====================

    private boolean invokeIsBlank(String value) throws Exception {
        var method = AuthServiceImpl.class.getDeclaredMethod("isBlank", String.class);
        method.setAccessible(true);
        return (boolean) method.invoke(service, value);
    }

    private String invokeResolveNickname(RegisterRequest request) throws Exception {
        var method = AuthServiceImpl.class.getDeclaredMethod("resolveNickname", RegisterRequest.class);
        method.setAccessible(true);
        return (String) method.invoke(service, request);
    }

    private void invokeValidateRegisterRequest(RegisterRequest request) throws Exception {
        var method = AuthServiceImpl.class.getDeclaredMethod("validateRegisterRequest", RegisterRequest.class);
        method.setAccessible(true);
        try {
            method.invoke(service, request);
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw (Exception) e.getCause();
        }
    }
}
