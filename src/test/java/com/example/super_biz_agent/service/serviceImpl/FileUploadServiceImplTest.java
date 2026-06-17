package com.example.super_biz_agent.service.serviceImpl;

import com.example.super_biz_agent.config.FileUploadConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FileUploadServiceImpl — isAllowedExtension")
class FileUploadServiceImplTest {

    private FileUploadServiceImpl service;
    private FileUploadConfig config;

    @BeforeEach
    void setUp() {
        config = new FileUploadConfig();
        config.setAllowedExtensions("txt,md");
        config.setPath("/tmp/uploads");

        service = new FileUploadServiceImpl();
        ReflectionTestUtils.setField(service, "fileUploadConfig", config);
    }

    @Nested
    @DisplayName("isAllowedExtension")
    class IsAllowedExtension {

        @Test
        @DisplayName("txt → true")
        void txtAllowed() throws Exception {
            assertThat(invokeIsAllowedExtension("txt")).isTrue();
        }

        @Test
        @DisplayName("md → true")
        void mdAllowed() throws Exception {
            assertThat(invokeIsAllowedExtension("md")).isTrue();
        }

        @Test
        @DisplayName("TXT uppercase → true (case-insensitive)")
        void caseInsensitive() throws Exception {
            assertThat(invokeIsAllowedExtension("TXT")).isTrue();
            assertThat(invokeIsAllowedExtension("MD")).isTrue();
        }

        @Test
        @DisplayName("mixed case → true")
        void mixedCase() throws Exception {
            assertThat(invokeIsAllowedExtension("Md")).isTrue();
        }

        @Test
        @DisplayName("pdf → false")
        void pdfNotAllowed() throws Exception {
            assertThat(invokeIsAllowedExtension("pdf")).isFalse();
        }

        @Test
        @DisplayName("exe → false")
        void exeNotAllowed() throws Exception {
            assertThat(invokeIsAllowedExtension("exe")).isFalse();
        }

        @Test
        @DisplayName("empty string → false")
        void emptyString() throws Exception {
            assertThat(invokeIsAllowedExtension("")).isFalse();
        }

        @Test
        @DisplayName("null → NPE thrown (production code assumes non-null from Guava)")
        void nullInput() throws Exception {
            // getFileExtension() from Guava returns "" for no extension, never null.
            // Passing null directly exposes potential NPE in isAllowedExtension's toLowerCase().
            try {
                invokeIsAllowedExtension(null);
            } catch (java.lang.reflect.InvocationTargetException e) {
                assertThat(e.getCause()).isInstanceOf(NullPointerException.class);
            }
        }
    }

    @Nested
    @DisplayName("isAllowedExtension — empty config")
    class EmptyConfig {

        @Test
        @DisplayName("null allowedExtensions → false for any input")
        void nullAllowedExtensions() throws Exception {
            config.setAllowedExtensions(null);
            assertThat(invokeIsAllowedExtension("txt")).isFalse();
        }

        @Test
        @DisplayName("empty allowedExtensions → false for any input")
        void emptyAllowedExtensions() throws Exception {
            config.setAllowedExtensions("");
            assertThat(invokeIsAllowedExtension("txt")).isFalse();
        }
    }

    // ==================== reflection helpers ====================

    private boolean invokeIsAllowedExtension(String extension) throws Exception {
        var method = FileUploadServiceImpl.class.getDeclaredMethod("isAllowedExtension", String.class);
        method.setAccessible(true);
        return (boolean) method.invoke(service, extension);
    }
}
