package com.example.super_biz_agent.service.serviceImpl;

import com.example.super_biz_agent.agent.tool.RagSearchTool;
import com.example.super_biz_agent.config.RagConfigProperties;
import com.example.super_biz_agent.mapper.ChatMessageMapper;
import com.example.super_biz_agent.mapper.ChatSessionMetaMapper;
import com.example.super_biz_agent.memory.ChatMemoryHelper;
import com.example.super_biz_agent.memory.RedisChatMemoryRepository;
import com.example.super_biz_agent.service.ChatMessagePersistenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("ChatServiceImpl — pure-logic methods")
class ChatServiceImplTest {

    private ChatServiceImpl service;
    private RagConfigProperties ragConfig;

    @BeforeEach
    void setUp() {
        ragConfig = new RagConfigProperties();
        ragConfig.setEnabled(true);
        ragConfig.setTopK(3);

        service = new ChatServiceImpl(
                mock(ChatModel.class),
                mock(RagSearchTool.class),
                ragConfig,
                mock(RedisChatMemoryRepository.class),
                mock(ChatMemoryHelper.class),
                mock(ChatMessageMapper.class),
                mock(ChatSessionMetaMapper.class),
                mock(ChatMessagePersistenceService.class)
        );

        ReflectionTestUtils.setField(service, "baseSystemPrompt", "你是一个有帮助的AI助手。");
        ReflectionTestUtils.setField(service, "baseSystemPromptWithRag", "你是业务知识助手，可以使用 queryInternalDocs 工具。");
    }

    // ==================== buildTitle ====================

    @Nested
    @DisplayName("buildTitle")
    class BuildTitle {

        @Test
        @DisplayName("short message → returns complete trimmed message")
        void shortMessage() throws Exception {
            String result = invokeBuildTitle("Hello");
            assertThat(result).isEqualTo("Hello");
        }

        @Test
        @DisplayName("exactly 30 chars → returns full string")
        void exactly30() throws Exception {
            String msg = "123456789012345678901234567890"; // 30 chars
            String result = invokeBuildTitle(msg);
            assertThat(result).hasSize(30).isEqualTo(msg);
        }

        @Test
        @DisplayName("31 chars → truncated to first 30")
        void over30Chars() throws Exception {
            String msg = "1234567890123456789012345678901"; // 31 chars
            String result = invokeBuildTitle(msg);
            assertThat(result).hasSize(30).isEqualTo(msg.substring(0, 30));
        }

        @Test
        @DisplayName("null → '新会话'")
        void nullMessage() throws Exception {
            String result = invokeBuildTitle(null);
            assertThat(result).isEqualTo("新会话");
        }

        @Test
        @DisplayName("empty → '新会话'")
        void emptyMessage() throws Exception {
            String result = invokeBuildTitle("");
            assertThat(result).isEqualTo("新会话");
        }

        @Test
        @DisplayName("blank → '新会话'")
        void blankMessage() throws Exception {
            String result = invokeBuildTitle("   ");
            assertThat(result).isEqualTo("新会话");
        }

        @Test
        @DisplayName("message with leading/trailing spaces → trimmed")
        void trimmed() throws Exception {
            String result = invokeBuildTitle("  Hello World  ");
            assertThat(result).isEqualTo("Hello World");
        }

        @Test
        @DisplayName("Chinese message ≤ 30 chars → full")
        void chineseShort() throws Exception {
            String result = invokeBuildTitle("这是一条中文消息");
            assertThat(result).isEqualTo("这是一条中文消息");
        }

        @Test
        @DisplayName("Chinese message > 30 chars → truncated")
        void chineseLong() throws Exception {
            // "这是一条非常非常长的中文消息用于测试标题的截断功能" = 21 chars, need > 30
            String msg = "这是一条真的非常非常非常长的中文消息用于测试标题截断功能是否正确";
            assertThat(msg.length()).isGreaterThan(30);
            String result = invokeBuildTitle(msg);
            assertThat(result).hasSize(30);
            assertThat(result).isEqualTo(msg.substring(0, 30));
        }

        @Test
        @DisplayName("message with newlines → preserved in trimmed")
        void withNewlines() throws Exception {
            String result = invokeBuildTitle("Line1\nLine2");
            assertThat(result).isEqualTo("Line1\nLine2");
        }
    }

    // ==================== buildSystemPrompt ====================

    @Nested
    @DisplayName("buildSystemPrompt")
    class BuildSystemPrompt {

        @Test
        @DisplayName("RAG enabled + null history → base prompt with RAG only")
        void ragEnabledNullHistory() throws Exception {
            String result = invokeBuildSystemPrompt(null);
            assertThat(result).contains("queryInternalDocs");
            assertThat(result).doesNotContain("历史对话");
        }

        @Test
        @DisplayName("RAG enabled + blank history → base prompt with RAG only")
        void ragEnabledBlankHistory() throws Exception {
            String result = invokeBuildSystemPrompt("   ");
            assertThat(result).contains("queryInternalDocs");
            assertThat(result).doesNotContain("历史对话");
        }

        @Test
        @DisplayName("RAG enabled + valid history → base RAG prompt + history block")
        void ragEnabledWithHistory() throws Exception {
            String result = invokeBuildSystemPrompt("User: Hello\nAI: Hi!");
            assertThat(result).contains("queryInternalDocs");
            assertThat(result).contains("--- 历史对话 ---");
            assertThat(result).contains("User: Hello");
            assertThat(result).contains("--- 历史对话结束 ---");
        }

        @Test
        @DisplayName("RAG disabled + null history → base prompt without RAG")
        void ragDisabledNullHistory() throws Exception {
            ragConfig.setEnabled(false);
            String result = invokeBuildSystemPrompt(null);
            assertThat(result).contains("有帮助的AI助手");
            assertThat(result).doesNotContain("queryInternalDocs");
        }

        @Test
        @DisplayName("RAG disabled + valid history → base prompt + history block")
        void ragDisabledWithHistory() throws Exception {
            ragConfig.setEnabled(false);
            String result = invokeBuildSystemPrompt("Previous conversation...");
            assertThat(result).contains("有帮助的AI助手");
            assertThat(result).contains("--- 历史对话 ---");
            assertThat(result).doesNotContain("queryInternalDocs");
        }
    }

    // ==================== reflection helpers ====================

    private String invokeBuildTitle(String message) throws Exception {
        var method = ChatServiceImpl.class.getDeclaredMethod("buildTitle", String.class);
        method.setAccessible(true);
        return (String) method.invoke(service, message);
    }

    private String invokeBuildSystemPrompt(String historyContext) throws Exception {
        var method = ChatServiceImpl.class.getDeclaredMethod("buildSystemPrompt", String.class);
        method.setAccessible(true);
        return (String) method.invoke(service, historyContext);
    }
}
