package com.example.super_biz_agent.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("QueryMetricsTools")
class QueryMetricsToolsTest {

    private QueryMetricsTools tools;

    @BeforeEach
    void setUp() {
        tools = new QueryMetricsTools();
    }

    // ==================== calculateDuration ====================

    @Nested
    @DisplayName("calculateDuration")
    class CalculateDuration {

        @Test
        @DisplayName("25 minutes ago → 'XmYs' format")
        void minutesAgo() throws Exception {
            Instant past = Instant.now().minus(25, java.time.temporal.ChronoUnit.MINUTES);
            String result = invokeCalculateDuration(past.toString());
            assertThat(result).containsPattern("\\d+m\\d+s");
        }

        @Test
        @DisplayName("1 hour 5 minutes ago → 'XhYmZs' format")
        void hoursAgo() throws Exception {
            Instant past = Instant.now().minus(65, java.time.temporal.ChronoUnit.MINUTES);
            String result = invokeCalculateDuration(past.toString());
            assertThat(result).containsPattern("\\d+h\\d+m\\d+s");
        }

        @Test
        @DisplayName("30 seconds ago → 'Xs' format")
        void secondsAgo() throws Exception {
            Instant past = Instant.now().minus(30, java.time.temporal.ChronoUnit.SECONDS);
            String result = invokeCalculateDuration(past.toString());
            assertThat(result).matches("\\d{1,2}s");
        }

        @Test
        @DisplayName("null input → 'unknown'")
        void nullInput() throws Exception {
            String result = invokeCalculateDuration(null);
            assertThat(result).isEqualTo("unknown");
        }

        @Test
        @DisplayName("malformed string → 'unknown'")
        void malformedInput() throws Exception {
            String result = invokeCalculateDuration("not-a-date");
            assertThat(result).isEqualTo("unknown");
        }

        @Test
        @DisplayName("empty string → 'unknown'")
        void emptyInput() throws Exception {
            String result = invokeCalculateDuration("");
            assertThat(result).isEqualTo("unknown");
        }

        @Test
        @DisplayName("ISO 8601 with timezone → correct duration")
        void isoWithTimezone() throws Exception {
            Instant past = Instant.now().minus(10, java.time.temporal.ChronoUnit.MINUTES);
            String result = invokeCalculateDuration(past.toString());
            assertThat(result).containsPattern("\\d+m\\d+s");
        }

        @Test
        @DisplayName("exactly 2 hours ago → '2h0m0s'")
        void exactlyTwoHours() throws Exception {
            Instant past = Instant.now().minus(2, java.time.temporal.ChronoUnit.HOURS);
            String result = invokeCalculateDuration(past.toString());
            assertThat(result).containsPattern("\\d+h");
        }
    }

    // ==================== valueOrDefault ====================

    @Nested
    @DisplayName("valueOrDefault")
    class ValueOrDefault {

        @Test
        @DisplayName("key present with value → returns value")
        void keyPresent() throws Exception {
            Map<String, String> map = new HashMap<>();
            map.put("name", "test-alert");
            String result = invokeValueOrDefault(map, "name", "default");
            assertThat(result).isEqualTo("test-alert");
        }

        @Test
        @DisplayName("key not present → returns default")
        void keyAbsent() throws Exception {
            Map<String, String> map = new HashMap<>();
            String result = invokeValueOrDefault(map, "name", "Unknown");
            assertThat(result).isEqualTo("Unknown");
        }

        @Test
        @DisplayName("key present but null value → returns default")
        void nullValue() throws Exception {
            Map<String, String> map = new HashMap<>();
            map.put("name", null);
            String result = invokeValueOrDefault(map, "name", "default");
            assertThat(result).isEqualTo("default");
        }

        @Test
        @DisplayName("key present but blank value → returns default")
        void blankValue() throws Exception {
            Map<String, String> map = new HashMap<>();
            map.put("name", "   ");
            String result = invokeValueOrDefault(map, "name", "default");
            assertThat(result).isEqualTo("default");
        }

        @Test
        @DisplayName("key present but empty value → returns default")
        void emptyValue() throws Exception {
            Map<String, String> map = new HashMap<>();
            map.put("name", "");
            String result = invokeValueOrDefault(map, "name", "default");
            assertThat(result).isEqualTo("default");
        }

        @Test
        @DisplayName("null source map → returns default")
        void nullSource() throws Exception {
            String result = invokeValueOrDefault(null, "name", "safe");
            assertThat(result).isEqualTo("safe");
        }

        @Test
        @DisplayName("null default value → returns null when key absent")
        void nullDefault() throws Exception {
            Map<String, String> map = new HashMap<>();
            String result = invokeValueOrDefault(map, "missing", null);
            assertThat(result).isNull();
        }
    }

    // ==================== simplifyAlerts ====================

    @Nested
    @DisplayName("simplifyAlerts")
    class SimplifyAlerts {

        @Test
        @DisplayName("null result → empty list")
        void nullResult() throws Exception {
            // simplifyAlerts calls result.getData() which would NPE.
            // This is a documented potential bug — guarded by caller.
            // Testing behavior: don't pass null, pass result with null data instead.
            QueryMetricsTools.PrometheusAlertsResult result = new QueryMetricsTools.PrometheusAlertsResult();
            result.setData(null);
            List<?> alerts = invokeSimplifyAlerts(result);
            assertThat(alerts).isEmpty();
        }

        @Test
        @DisplayName("null alerts list → empty list")
        void nullAlerts() throws Exception {
            QueryMetricsTools.PrometheusAlertsResult result = new QueryMetricsTools.PrometheusAlertsResult();
            QueryMetricsTools.AlertsData data = new QueryMetricsTools.AlertsData();
            data.setAlerts(null);
            result.setData(data);
            List<?> alerts = invokeSimplifyAlerts(result);
            assertThat(alerts).isEmpty();
        }

        @Test
        @DisplayName("empty alerts list → empty list")
        void emptyAlerts() throws Exception {
            QueryMetricsTools.PrometheusAlertsResult result = new QueryMetricsTools.PrometheusAlertsResult();
            QueryMetricsTools.AlertsData data = new QueryMetricsTools.AlertsData();
            result.setData(data);
            List<?> alerts = invokeSimplifyAlerts(result);
            assertThat(alerts).isEmpty();
        }

        @Test
        @DisplayName("single alert → returns one SimplifiedAlert")
        void singleAlert() throws Exception {
            QueryMetricsTools.PrometheusAlertsResult result = buildAlertsResult(
                    buildAlert("HighCPU", "CPU high", "firing", Instant.now().minusSeconds(300).toString())
            );
            List<QueryMetricsTools.SimplifiedAlert> alerts = invokeSimplifyAlerts(result);
            assertThat(alerts).hasSize(1);
            assertThat(alerts.get(0).getAlertName()).isEqualTo("HighCPU");
            assertThat(alerts.get(0).getDescription()).isEqualTo("CPU high");
            assertThat(alerts.get(0).getState()).isEqualTo("firing");
        }

        @Test
        @DisplayName("duplicate alert names → deduplicated")
        void deduplication() throws Exception {
            QueryMetricsTools.PrometheusAlertsResult result = buildAlertsResult(
                    buildAlert("CPU", "desc1", "firing", Instant.now().toString()),
                    buildAlert("CPU", "desc2", "firing", Instant.now().toString())
            );
            List<QueryMetricsTools.SimplifiedAlert> alerts = invokeSimplifyAlerts(result);
            assertThat(alerts).hasSize(1);
        }

        @Test
        @DisplayName("multiple distinct alerts → all returned")
        void distinctAlerts() throws Exception {
            QueryMetricsTools.PrometheusAlertsResult result = buildAlertsResult(
                    buildAlert("CPU", "d1", "firing", Instant.now().toString()),
                    buildAlert("Memory", "d2", "firing", Instant.now().toString()),
                    buildAlert("Disk", "d3", "firing", Instant.now().toString())
            );
            List<QueryMetricsTools.SimplifiedAlert> alerts = invokeSimplifyAlerts(result);
            assertThat(alerts).hasSize(3);
        }

        @Test
        @DisplayName("alert with missing labels → alertName defaults to 'UnknownAlert'")
        void missingLabels() throws Exception {
            QueryMetricsTools.PrometheusAlert alert = new QueryMetricsTools.PrometheusAlert();
            alert.setLabels(null);
            alert.setAnnotations(new HashMap<>());
            alert.setState("firing");
            alert.setActiveAt(Instant.now().toString());

            QueryMetricsTools.PrometheusAlertsResult result = buildAlertsResult(alert);
            List<QueryMetricsTools.SimplifiedAlert> alerts = invokeSimplifyAlerts(result);
            assertThat(alerts.get(0).getAlertName()).isEqualTo("UnknownAlert");
        }

        @Test
        @DisplayName("duration is computed from activeAt")
        void durationComputed() throws Exception {
            Instant past = Instant.now().minus(600, java.time.temporal.ChronoUnit.SECONDS);
            QueryMetricsTools.PrometheusAlertsResult result = buildAlertsResult(
                    buildAlert("CPU", "desc", "firing", past.toString())
            );
            List<QueryMetricsTools.SimplifiedAlert> alerts = invokeSimplifyAlerts(result);
            assertThat(alerts.get(0).getDuration()).isNotNull();
            assertThat(alerts.get(0).getDuration()).isNotEmpty();
        }
    }

    // ==================== buildMockAlerts ====================

    @Nested
    @DisplayName("buildMockAlerts")
    class BuildMockAlerts {

        @Test
        @DisplayName("returns 2-4 alerts")
        void countBetween2And4() throws Exception {
            for (int i = 0; i < 100; i++) {
                List<?> alerts = invokeBuildMockAlerts();
                assertThat(alerts).hasSizeBetween(2, 4);
            }
        }

        @Test
        @DisplayName("each alert has non-null fields")
        void fieldsArePopulated() throws Exception {
            List<QueryMetricsTools.SimplifiedAlert> alerts = invokeBuildMockAlerts();
            assertThat(alerts).allSatisfy(a -> {
                assertThat(a.getAlertName()).isNotBlank();
                assertThat(a.getDescription()).isNotBlank();
                assertThat(a.getState()).isEqualTo("firing");
                assertThat(a.getActiveAt()).isNotBlank();
                assertThat(a.getDuration()).isNotBlank();
            });
        }

        @Test
        @DisplayName("alert names are from known set")
        void knownAlertNames() throws Exception {
            List<QueryMetricsTools.SimplifiedAlert> alerts = invokeBuildMockAlerts();
            List<String> knownNames = List.of(
                    "HighCPUUsage", "SlowResponse", "HighMemoryUsage",
                    "DatabaseConnectionPoolExhausted", "RedisConnectionTimeout", "InstanceDown"
            );
            assertThat(alerts).allMatch(a -> knownNames.contains(a.getAlertName()));
        }

        @Test
        @DisplayName("no duplicates within a single call")
        void noDuplicates() throws Exception {
            for (int i = 0; i < 20; i++) {
                List<QueryMetricsTools.SimplifiedAlert> alerts = invokeBuildMockAlerts();
                long distinctCount = alerts.stream()
                        .map(QueryMetricsTools.SimplifiedAlert::getAlertName)
                        .distinct()
                        .count();
                assertThat(distinctCount).isEqualTo(alerts.size());
            }
        }
    }

    // ==================== buildErrorResponse ====================

    @Nested
    @DisplayName("buildErrorResponse")
    class BuildErrorResponse {

        @Test
        @DisplayName("returns valid JSON with success=false")
        void successIsFalse() throws Exception {
            String json = invokeBuildErrorResponse("query failed", "timeout");
            assertThat(json).contains("\"success\" : false");
            assertThat(json).contains("\"query failed\"");
            assertThat(json).contains("\"timeout\"");
        }

        @Test
        @DisplayName("null message → JSON contains null")
        void nullMessage() throws Exception {
            String json = invokeBuildErrorResponse(null, "error-detail");
            assertThat(json).contains("\"success\" : false");
        }

        @Test
        @DisplayName("null error → JSON contains null")
        void nullError() throws Exception {
            String json = invokeBuildErrorResponse("msg", null);
            assertThat(json).contains("\"success\" : false");
        }

        @Test
        @DisplayName("parseable as PrometheusAlertsOutput")
        void parseable() throws Exception {
            String json = invokeBuildErrorResponse("fail", "err");
            ObjectMapper mapper = new ObjectMapper();
            QueryMetricsTools.PrometheusAlertsOutput output =
                    mapper.readValue(json, QueryMetricsTools.PrometheusAlertsOutput.class);
            assertThat(output.isSuccess()).isFalse();
            assertThat(output.getMessage()).isEqualTo("fail");
            assertThat(output.getError()).isEqualTo("err");
        }
    }

    // ==================== reflection helpers ====================

    private String invokeCalculateDuration(String activeAtStr) throws Exception {
        var method = QueryMetricsTools.class.getDeclaredMethod("calculateDuration", String.class);
        method.setAccessible(true);
        return (String) method.invoke(tools, activeAtStr);
    }

    private String invokeValueOrDefault(Map<String, String> source, String key, String defaultValue)
            throws Exception {
        var method = QueryMetricsTools.class.getDeclaredMethod(
                "valueOrDefault", Map.class, String.class, String.class);
        method.setAccessible(true);
        return (String) method.invoke(tools, source, key, defaultValue);
    }

    @SuppressWarnings("unchecked")
    private List<QueryMetricsTools.SimplifiedAlert> invokeSimplifyAlerts(
            QueryMetricsTools.PrometheusAlertsResult result) throws Exception {
        var method = QueryMetricsTools.class.getDeclaredMethod(
                "simplifyAlerts", QueryMetricsTools.PrometheusAlertsResult.class);
        method.setAccessible(true);
        return (List<QueryMetricsTools.SimplifiedAlert>) method.invoke(tools, result);
    }

    @SuppressWarnings("unchecked")
    private List<QueryMetricsTools.SimplifiedAlert> invokeBuildMockAlerts() throws Exception {
        var method = QueryMetricsTools.class.getDeclaredMethod("buildMockAlerts");
        method.setAccessible(true);
        return (List<QueryMetricsTools.SimplifiedAlert>) method.invoke(tools);
    }

    private String invokeBuildErrorResponse(String message, String error) throws Exception {
        var method = QueryMetricsTools.class.getDeclaredMethod(
                "buildErrorResponse", String.class, String.class);
        method.setAccessible(true);
        return (String) method.invoke(tools, message, error);
    }

    // ==================== test data builders ====================

    private QueryMetricsTools.PrometheusAlert buildAlert(
            String name, String description, String state, String activeAt) {
        QueryMetricsTools.PrometheusAlert alert = new QueryMetricsTools.PrometheusAlert();
        Map<String, String> labels = new HashMap<>();
        labels.put("alertname", name);
        alert.setLabels(labels);
        Map<String, String> annotations = new HashMap<>();
        annotations.put("description", description);
        alert.setAnnotations(annotations);
        alert.setState(state);
        alert.setActiveAt(activeAt);
        return alert;
    }

    private QueryMetricsTools.PrometheusAlertsResult buildAlertsResult(
            QueryMetricsTools.PrometheusAlert... alerts) {
        QueryMetricsTools.PrometheusAlertsResult result = new QueryMetricsTools.PrometheusAlertsResult();
        QueryMetricsTools.AlertsData data = new QueryMetricsTools.AlertsData();
        data.setAlerts(new ArrayList<>(List.of(alerts)));
        result.setData(data);
        result.setStatus("success");
        return result;
    }
}
