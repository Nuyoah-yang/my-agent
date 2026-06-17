package com.example.super_biz_agent.service.serviceImpl;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.example.super_biz_agent.agent.tool.QueryMetricsTools;
import com.example.super_biz_agent.agent.tool.RagSearchTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("AiOpsServiceImpl — pure-logic methods")
class AiOpsServiceImplTest {

    private AiOpsServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AiOpsServiceImpl(
                mock(ChatModel.class),
                mock(QueryMetricsTools.class),
                mock(RagSearchTool.class)
        );
    }

    // ==================== buildTaskPrompt ====================

    @Nested
    @DisplayName("buildTaskPrompt")
    class BuildTaskPrompt {

        @Test
        @DisplayName("non-empty string")
        void nonEmpty() throws Exception {
            String result = invokeBuildTaskPrompt();
            assertThat(result).isNotBlank();
        }

        @Test
        @DisplayName("contains SRE and plan/execute/finish instructions")
        void containsKeyInstructions() throws Exception {
            String result = invokeBuildTaskPrompt();
            assertThat(result).contains("SRE");
            assertThat(result).contains("PLAN");
            assertThat(result).contains("EXECUTE");
            assertThat(result).contains("FINISH");
        }

        @Test
        @DisplayName("references queryPrometheusAlerts tool")
        void referencesPrometheusTool() throws Exception {
            String result = invokeBuildTaskPrompt();
            assertThat(result).contains("queryPrometheusAlerts");
        }

        @Test
        @DisplayName("references queryInternalDocs tool")
        void referencesRagTool() throws Exception {
            String result = invokeBuildTaskPrompt();
            assertThat(result).contains("queryInternalDocs");
        }
    }

    // ==================== buildPlannerPrompt ====================

    @Nested
    @DisplayName("buildPlannerPrompt")
    class BuildPlannerPrompt {

        @Test
        @DisplayName("non-empty string")
        void nonEmpty() throws Exception {
            String result = invokeBuildPlannerPrompt();
            assertThat(result).isNotBlank();
        }

        @Test
        @DisplayName("contains Planner Agent instructions")
        void containsPlannerRole() throws Exception {
            String result = invokeBuildPlannerPrompt();
            assertThat(result).contains("Planner Agent");
            assertThat(result).contains("告警分析报告");
        }

        @Test
        @DisplayName("contains decision types: PLAN / EXECUTE / FINISH")
        void containsDecisions() throws Exception {
            String result = invokeBuildPlannerPrompt();
            assertThat(result).contains("PLAN");
            assertThat(result).contains("EXECUTE");
            assertThat(result).contains("FINISH");
        }

        @Test
        @DisplayName("contains Markdown report template sections")
        void containsReportTemplate() throws Exception {
            String result = invokeBuildPlannerPrompt();
            assertThat(result).contains("# 告警分析报告");
            assertThat(result).contains("📋 活跃告警清单");
            assertThat(result).contains("🔍 告警根因分析");
            assertThat(result).contains("🛠️ 处理建议");
        }
    }

    // ==================== buildExecutorPrompt ====================

    @Nested
    @DisplayName("buildExecutorPrompt")
    class BuildExecutorPrompt {

        @Test
        @DisplayName("non-empty string")
        void nonEmpty() throws Exception {
            String result = invokeBuildExecutorPrompt();
            assertThat(result).isNotBlank();
        }

        @Test
        @DisplayName("contains Executor Agent role")
        void containsExecutorRole() throws Exception {
            String result = invokeBuildExecutorPrompt();
            assertThat(result).contains("Executor Agent");
        }

        @Test
        @DisplayName("contains SUCCESS|FAILED states")
        void containsStates() throws Exception {
            String result = invokeBuildExecutorPrompt();
            assertThat(result).contains("SUCCESS");
            assertThat(result).contains("FAILED");
        }

        @Test
        @DisplayName("contains JSON format hint")
        void containsJsonFormat() throws Exception {
            String result = invokeBuildExecutorPrompt();
            assertThat(result).contains("JSON");
            assertThat(result).contains("evidence");
        }
    }

    // ==================== buildSupervisorPrompt ====================

    @Nested
    @DisplayName("buildSupervisorPrompt")
    class BuildSupervisorPrompt {

        @Test
        @DisplayName("non-empty string")
        void nonEmpty() throws Exception {
            String result = invokeBuildSupervisorPrompt();
            assertThat(result).isNotBlank();
        }

        @Test
        @DisplayName("contains Supervisor role")
        void containsSupervisorRole() throws Exception {
            String result = invokeBuildSupervisorPrompt();
            assertThat(result).contains("AI Ops Supervisor");
        }

        @Test
        @DisplayName("references planner_agent and executor_agent")
        void containsAgentNames() throws Exception {
            String result = invokeBuildSupervisorPrompt();
            assertThat(result).contains("planner_agent");
            assertThat(result).contains("executor_agent");
        }

        @Test
        @DisplayName("contains FINISH termination condition")
        void containsFinish() throws Exception {
            String result = invokeBuildSupervisorPrompt();
            assertThat(result).contains("FINISH");
        }
    }

    // ==================== extractFinalReport ====================

    @Nested
    @DisplayName("extractFinalReport")
    class ExtractFinalReport {

        @Test
        @DisplayName("state with valid planner_plan containing AssistantMessage → returns text")
        void validPlannerPlan() throws Exception {
            OverAllState state = mock(OverAllState.class);
            AssistantMessage msg = new AssistantMessage("# Report\nThis is the final report.");
            when(state.value("planner_plan")).thenReturn(Optional.of(msg));

            Optional<String> result = invokeExtractFinalReport(state);
            assertThat(result).isPresent();
            assertThat(result.get()).contains("# Report");
        }

        @Test
        @DisplayName("state without planner_plan key → empty")
        void missingPlannerPlan() throws Exception {
            OverAllState state = mock(OverAllState.class);
            when(state.value("planner_plan")).thenReturn(Optional.empty());

            Optional<String> result = invokeExtractFinalReport(state);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("planner_plan with non-AssistantMessage → empty")
        void nonAssistantMessage() throws Exception {
            OverAllState state = mock(OverAllState.class);
            when(state.value("planner_plan")).thenReturn(Optional.of("not an AssistantMessage"));

            Optional<String> result = invokeExtractFinalReport(state);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("AssistantMessage with null text → empty")
        void nullText() throws Exception {
            OverAllState state = mock(OverAllState.class);
            AssistantMessage msg = new AssistantMessage((String) null);
            when(state.value("planner_plan")).thenReturn(Optional.of(msg));

            Optional<String> result = invokeExtractFinalReport(state);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("AssistantMessage with blank text → empty")
        void blankText() throws Exception {
            OverAllState state = mock(OverAllState.class);
            AssistantMessage msg = new AssistantMessage("   ");
            when(state.value("planner_plan")).thenReturn(Optional.of(msg));

            Optional<String> result = invokeExtractFinalReport(state);
            assertThat(result).isEmpty();
        }
    }

    // ==================== reflection helpers ====================

    private String invokeBuildTaskPrompt() throws Exception {
        var method = AiOpsServiceImpl.class.getDeclaredMethod("buildTaskPrompt");
        method.setAccessible(true);
        return (String) method.invoke(service);
    }

    private String invokeBuildPlannerPrompt() throws Exception {
        var method = AiOpsServiceImpl.class.getDeclaredMethod("buildPlannerPrompt");
        method.setAccessible(true);
        return (String) method.invoke(service);
    }

    private String invokeBuildExecutorPrompt() throws Exception {
        var method = AiOpsServiceImpl.class.getDeclaredMethod("buildExecutorPrompt");
        method.setAccessible(true);
        return (String) method.invoke(service);
    }

    private String invokeBuildSupervisorPrompt() throws Exception {
        var method = AiOpsServiceImpl.class.getDeclaredMethod("buildSupervisorPrompt");
        method.setAccessible(true);
        return (String) method.invoke(service);
    }

    @SuppressWarnings("unchecked")
    private Optional<String> invokeExtractFinalReport(OverAllState state) throws Exception {
        var method = AiOpsServiceImpl.class.getDeclaredMethod("extractFinalReport", OverAllState.class);
        method.setAccessible(true);
        return (Optional<String>) method.invoke(service, state);
    }
}
