package com.example.super_biz_agent.agent.tool;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Prometheus 告警查询工具。
 * 既支持 mock 模式联调，也支持真实调用 /api/v1/alerts。
 */
@Slf4j
@Component
public class QueryMetricsTools {

    public static final String TOOL_QUERY_PROMETHEUS_ALERTS = "queryPrometheusAlerts";

    @Value("${prometheus.base-url:http://localhost:9090}")
    private String prometheusBaseUrl;

    @Value("${prometheus.timeout:10}")
    private int timeoutSeconds;

    @Value("${prometheus.mock-enabled:true}")
    private boolean mockEnabled;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Tool(description = "Query active alerts from Prometheus alerting system. " +
            "Returns current firing alerts with alert_name, description, state, active_at and duration.")
    public String queryPrometheusAlerts() {
        log.info("开始查询 Prometheus 告警, mockEnabled={}", mockEnabled);
        try {
            List<SimplifiedAlert> alerts;
            if (mockEnabled) {
                alerts = buildMockAlerts();
            } else {
                PrometheusAlertsResult result = fetchPrometheusAlerts();
                if (!"success".equalsIgnoreCase(result.getStatus())) {
                    return buildErrorResponse("Prometheus API 返回非成功状态", result.getError());
                }
                alerts = simplifyAlerts(result);
            }

            PrometheusAlertsOutput output = new PrometheusAlertsOutput();
            output.setSuccess(true);
            output.setAlerts(alerts);
            output.setMessage("成功检索到 " + alerts.size() + " 个活动告警");
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(output);
        } catch (Exception e) {
            log.error("查询 Prometheus 告警失败", e);
            return buildErrorResponse("查询失败", e.getMessage());
        }
    }

    private PrometheusAlertsResult fetchPrometheusAlerts() throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(prometheusBaseUrl + "/api/v1/alerts"))
                .GET()
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException("Prometheus HTTP 状态异常: " + response.statusCode());
        }
        return objectMapper.readValue(response.body(), PrometheusAlertsResult.class);
    }

    private List<SimplifiedAlert> simplifyAlerts(PrometheusAlertsResult result) {
        List<SimplifiedAlert> simplifiedAlerts = new ArrayList<>();
        Set<String> seenAlertNames = new HashSet<>();
        if (result.getData() == null || result.getData().getAlerts() == null) {
            return simplifiedAlerts;
        }
        for (PrometheusAlert alert : result.getData().getAlerts()) {
            String alertName = valueOrDefault(alert.getLabels(), "alertname", "UnknownAlert");
            if (seenAlertNames.contains(alertName)) {
                continue;
            }
            seenAlertNames.add(alertName);

            SimplifiedAlert simplified = new SimplifiedAlert();
            simplified.setAlertName(alertName);
            simplified.setDescription(valueOrDefault(alert.getAnnotations(), "description", ""));
            simplified.setState(alert.getState());
            simplified.setActiveAt(alert.getActiveAt());
            simplified.setDuration(calculateDuration(alert.getActiveAt()));
            simplifiedAlerts.add(simplified);
        }
        return simplifiedAlerts;
    }

    private String valueOrDefault(Map<String, String> source, String key, String defaultValue) {
        if (source == null) {
            return defaultValue;
        }
        String value = source.get(key);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private List<SimplifiedAlert> buildMockAlerts() {
        List<SimplifiedAlert> alerts = new ArrayList<>();
        Instant now = Instant.now();

        SimplifiedAlert cpuAlert = new SimplifiedAlert();
        cpuAlert.setAlertName("HighCPUUsage");
        cpuAlert.setDescription("payment-service CPU 使用率持续超过 80%，当前 92%。");
        cpuAlert.setState("firing");
        cpuAlert.setActiveAt(now.minus(25, ChronoUnit.MINUTES).toString());
        cpuAlert.setDuration(calculateDuration(cpuAlert.getActiveAt()));
        alerts.add(cpuAlert);

        SimplifiedAlert slowAlert = new SimplifiedAlert();
        slowAlert.setAlertName("SlowResponse");
        slowAlert.setDescription("user-service P99 响应时间超过 3 秒，当前 4.2 秒。");
        slowAlert.setState("firing");
        slowAlert.setActiveAt(now.minus(10, ChronoUnit.MINUTES).toString());
        slowAlert.setDuration(calculateDuration(slowAlert.getActiveAt()));
        alerts.add(slowAlert);

        return alerts;
    }

    private String calculateDuration(String activeAtStr) {
        try {
            Instant activeAt = Instant.parse(activeAtStr);
            Duration duration = Duration.between(activeAt, Instant.now());
            long hours = duration.toHours();
            long minutes = duration.toMinutes() % 60;
            long seconds = duration.getSeconds() % 60;
            if (hours > 0) {
                return String.format("%dh%dm%ds", hours, minutes, seconds);
            }
            if (minutes > 0) {
                return String.format("%dm%ds", minutes, seconds);
            }
            return String.format("%ds", seconds);
        } catch (Exception e) {
            return "unknown";
        }
    }

    private String buildErrorResponse(String message, String error) {
        try {
            PrometheusAlertsOutput output = new PrometheusAlertsOutput();
            output.setSuccess(false);
            output.setMessage(message);
            output.setError(error);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(output);
        } catch (Exception e) {
            return "{\"success\":false,\"message\":\"" + message + "\",\"error\":\"" + error + "\"}";
        }
    }

    @Data
    public static class PrometheusAlert {
        private Map<String, String> labels;
        private Map<String, String> annotations;
        private String state;
        private String activeAt;
        private String value;
    }

    @Data
    public static class PrometheusAlertsResult {
        private String status;
        private AlertsData data;
        private String error;
    }

    @Data
    public static class AlertsData {
        private List<PrometheusAlert> alerts = new ArrayList<>();
    }

    @Data
    public static class SimplifiedAlert {
        @JsonProperty("alert_name")
        private String alertName;

        @JsonProperty("description")
        private String description;

        @JsonProperty("state")
        private String state;

        @JsonProperty("active_at")
        private String activeAt;

        @JsonProperty("duration")
        private String duration;
    }

    @Data
    public static class PrometheusAlertsOutput {
        @JsonProperty("success")
        private boolean success;

        @JsonProperty("alerts")
        private List<SimplifiedAlert> alerts;

        @JsonProperty("message")
        private String message;

        @JsonProperty("error")
        private String error;
    }
}
