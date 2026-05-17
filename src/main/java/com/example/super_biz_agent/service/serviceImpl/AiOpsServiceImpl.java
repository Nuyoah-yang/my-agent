package com.example.super_biz_agent.service.serviceImpl;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.SupervisorAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.example.super_biz_agent.agent.tool.QueryMetricsTools;
import com.example.super_biz_agent.agent.tool.RagSearchTool;
import com.example.super_biz_agent.security.UserContextHolder;
import com.example.super_biz_agent.service.AiOpsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


@Slf4j
@Service
public class AiOpsServiceImpl implements AiOpsService {

    private final ChatModel chatModel;
    private final QueryMetricsTools queryMetricsTools;
    private final RagSearchTool ragSearchTool;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private static final int REPORT_CHUNK_SIZE = 80;

    public AiOpsServiceImpl(ChatModel chatModel,
                            QueryMetricsTools queryMetricsTools,
                            RagSearchTool ragSearchTool) {
        this.chatModel = chatModel;
        this.queryMetricsTools = queryMetricsTools;
        this.ragSearchTool = ragSearchTool;
    }

    @Override
    public SseEmitter aiOps() {
        SseEmitter emitter = new SseEmitter(600000L);
        Long currentUserId = UserContextHolder.getUserId();
        if (currentUserId == null) {
            sendError(emitter, "用户未登录，无法执行 AIOps 分析");
            emitter.complete();
            return emitter;
        }
        executor.execute(() -> {
            try {
                log.info("开始执行 AIOps 分析流程, userId={}", currentUserId);
                sendContent(emitter, "正在读取告警并拆解任务...\n");
                sendContent(emitter, "正在启动多 Agent 编排（Planner / Executor / Supervisor）...\n");
                Optional<OverAllState> overAllStateOptional = runAiOpsWorkflow();
                if (overAllStateOptional.isEmpty()) {
                    throw new IllegalStateException("多 Agent 编排未获取到有效结果");
                }
                String report = extractFinalReport(overAllStateOptional.get())
                        .orElseThrow(() -> new IllegalStateException("未能提取到最终告警分析报告"));
                sendContent(emitter, "\n正在生成告警分析报告...\n\n");
                for (int i = 0; i < report.length(); i += REPORT_CHUNK_SIZE) {
                    int end = Math.min(i + REPORT_CHUNK_SIZE, report.length());
                    sendContent(emitter, report.substring(i, end));
                }

                sendDone(emitter);
                emitter.complete();
                log.info("AIOps 分析流程完成, userId={}", currentUserId);
            } catch (Exception e) {
                sendError(emitter, "AIOps 流程失败: " + e.getMessage());
                emitter.completeWithError(e);
                log.error("AIOps 流程异常, userId={}", currentUserId, e);
            }
        });

        return emitter;
    }

    private Optional<OverAllState> runAiOpsWorkflow() throws GraphRunnerException {
        ReactAgent plannerAgent = ReactAgent.builder()
                .name("planner_agent")
                .model(chatModel)
                .description("负责拆解告警、规划步骤、汇总最终报告")
                .systemPrompt(buildPlannerPrompt())
                .methodTools(new Object[]{queryMetricsTools, ragSearchTool})
                .outputKey("planner_plan")
                .build();

        ReactAgent executorAgent = ReactAgent.builder()
                .name("executor_agent")
                .model(chatModel)
                .description("负责执行 Planner 指定的首个步骤并返回证据")
                .systemPrompt(buildExecutorPrompt())
                .methodTools(new Object[]{queryMetricsTools, ragSearchTool})
                .outputKey("executor_feedback")
                .build();

        SupervisorAgent supervisorAgent = SupervisorAgent.builder()
                .name("ai_ops_supervisor")
                .description("负责调度 Planner 与 Executor 的多 Agent 控制器")
                .model(chatModel)
                .systemPrompt(buildSupervisorPrompt())
                .subAgents(List.of(plannerAgent, executorAgent))
                .build();

        return supervisorAgent.invoke(buildTaskPrompt());
    }

    private Optional<String> extractFinalReport(OverAllState state) {
        Optional<AssistantMessage> plannerFinalOutput = state.value("planner_plan")
                .filter(AssistantMessage.class::isInstance)
                .map(AssistantMessage.class::cast);
        if (plannerFinalOutput.isPresent()) {
            String text = plannerFinalOutput.get().getText();
            if (text != null && !text.isBlank()) {
                return Optional.of(text);
            }
        }
        return Optional.empty();
    }

    private String buildTaskPrompt() {
        return """
                你是企业级 SRE，接到自动化告警排查任务。请执行一个闭环：
                1. 先规划排查步骤（PLAN）；
                2. 执行首个步骤并返回证据（EXECUTE）；
                3. 根据证据再规划，直到能输出最终报告（FINISH）。
                必须优先调用 queryPrometheusAlerts 获取活跃告警，必要时调用 queryInternalDocs 补充知识依据。
                严禁编造未查询到的数据，若工具失败请明确给出无法完成原因。
                """;
    }

    private String buildPlannerPrompt() {
        return """
                你是 Planner Agent，同时承担 Replanner 角色，负责：
                1. 阅读任务输入以及 Executor 最新反馈；
                2. 选择 decision：PLAN / EXECUTE / FINISH；
                3. 当 decision=EXECUTE 时，明确下一步和需要调用的工具；
                4. 当 decision=FINISH 时，直接输出最终 Markdown 报告。

                如果工具连续失败或返回空数据，请在最终报告中如实说明“无法完成原因”。

                最终报告模板如下：

                # 告警分析报告

                ---

                ## 📋 活跃告警清单
                使用表格列出告警名称、级别、目标服务、首次触发时间、最新触发时间、状态。

                ---

                ## 🔍 告警根因分析
                - 症状描述
                - 日志/指标证据（若没有日志工具，请明确说明）
                - 根因结论

                ---

                ## 🛠️ 处理建议
                1. 立即止损动作
                2. 短期优化建议
                3. 长期治理建议

                ---

                ## 📊 结论
                - 整体风险评估
                - 关键发现
                - 未完成项与原因（若存在工具失败或数据缺失）

                输出要求：
                - decision=FINISH 时直接输出 Markdown，不要输出 JSON。
                - 必须基于工具返回的数据，不得虚构具体指标值。
                - 当工具失败时，报告中必须明确“无法完成的原因”。
                """;
    }

    private String buildExecutorPrompt() {
        return """
                你是 Executor Agent，负责执行 Planner 指定的第一步，并返回结构化反馈。
                只允许做一件事：执行首步并反馈证据，不能直接输出最终报告。
                返回格式建议（JSON 文本）：
                {
                  "status": "SUCCESS|FAILED",
                  "summary": "执行摘要",
                  "evidence": "关键证据",
                  "nextHint": "给 Planner 的下一步建议"
                }
                若工具报错或无数据，请写清失败原因，不得编造。
                """;
    }

    private String buildSupervisorPrompt() {
        return """
                你是 AI Ops Supervisor，负责在 planner_agent、executor_agent 与 FINISH 之间调度。
                调度规则：
                1. 需要规划时调用 planner_agent；
                2. planner_agent 给出 EXECUTE 时调用 executor_agent；
                3. 根据 executor_agent 反馈继续调用 planner_agent；
                4. planner_agent 决策 FINISH 后结束流程并输出最终报告。
                禁止编造任何工具未返回的数据。
                """;
    }

    private void sendContent(SseEmitter emitter, String content) {
        sendEvent(emitter, "content", content);
    }

    private void sendError(SseEmitter emitter, String errorMessage) {
        sendEvent(emitter, "error", errorMessage);
    }

    private void sendDone(SseEmitter emitter) {
        sendEvent(emitter, "done", null);
    }

    private void sendEvent(SseEmitter emitter, String type, String data) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", type);
        payload.put("data", data);
        try {
            emitter.send(SseEmitter.event().name("message").data(payload, MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            throw new RuntimeException("SSE 发送失败", e);
        }
    }
}
