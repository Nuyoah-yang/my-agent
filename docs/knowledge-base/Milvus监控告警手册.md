# Milvus 监控告警手册

## 一、监控架构

### 1.1 监控体系

```
┌─────────────────────────────────────────────────────────────┐
│                      监控架构                               │
├─────────────────────────────────────────────────────────────┤
│  [Milvus Server]  ──metrics──→  [Prometheus]              │
│       │                           │                        │
│       │ healthz                   │                        │
│       ↓                           ↓                        │
│  [Grafana]  ←──dashboard──       │                        │
│       │                           │                        │
│       │ alert                     ↓                        │
│       ↓                   [Alertmanager]                  │
│  [Notification]  ←──alert──                               │
└─────────────────────────────────────────────────────────────┘
```

### 1.2 监控组件

| 组件 | 职责 | 端口 |
|-----|------|-----|
| **Milvus** | 暴露 metrics | 9091 |
| **Prometheus** | 采集和存储指标 | 9090 |
| **Grafana** | 可视化仪表盘 | 3000 |
| **Alertmanager** | 告警管理 | 9093 |

---

## 二、指标采集

### 2.1 Milvus Metrics 端点

**访问地址**：`http://<milvus_host>:9091/metrics`

**指标分类**：

| 类别 | 指标前缀 | 说明 |
|-----|---------|------|
| **服务状态** | `milvus_service_*` | 服务健康、版本等 |
| **查询指标** | `milvus_query_*` | QPS、延迟、成功率 |
| **插入指标** | `milvus_insert_*` | QPS、延迟、成功率 |
| **索引指标** | `milvus_index_*` | 索引构建、加载状态 |
| **内存指标** | `milvus_memory_*` | 内存使用情况 |
| **存储指标** | `milvus_storage_*` | 存储使用情况 |
| **系统指标** | `milvus_system_*` | CPU、网络等 |

---

### 2.2 Prometheus 配置

**配置示例（prometheus.yml）**：

```yaml
global:
  scrape_interval: 60s
  evaluation_interval: 60s

scrape_configs:
  - job_name: "milvus"
    scrape_timeout: 10s
    metrics_path: /metrics
    static_configs:
      - targets: ["127.0.0.1:9091"]
    relabel_configs:
      - source_labels: [__name__]
        regex: '^(milvus_.*|up)$'
        action: keep
```

**关键配置说明**：
- `scrape_interval`：采集间隔（建议 60s）
- `scrape_timeout`：采集超时（建议 10s）
- `relabel_configs`：指标过滤，只保留必要指标

---

### 2.3 核心指标清单

#### 查询性能指标

| 指标名 | 类型 | 说明 | 单位 |
|-------|-----|------|-----|
| `milvus_query_qps` | Counter | 查询 QPS | ops/s |
| `milvus_query_latency_seconds` | Histogram | 查询延迟 | s |
| `milvus_query_success_rate` | Gauge | 查询成功率 | % |
| `milvus_query_topk` | Histogram | 查询返回数量 | - |

#### 插入性能指标

| 指标名 | 类型 | 说明 | 单位 |
|-------|-----|------|-----|
| `milvus_insert_qps` | Counter | 插入 QPS | ops/s |
| `milvus_insert_latency_seconds` | Histogram | 插入延迟 | s |
| `milvus_insert_success_rate` | Gauge | 插入成功率 | % |

#### 资源使用指标

| 指标名 | 类型 | 说明 | 单位 |
|-------|-----|------|-----|
| `milvus_memory_usage_bytes` | Gauge | 内存使用 | bytes |
| `milvus_memory_limit_bytes` | Gauge | 内存限制 | bytes |
| `milvus_cpu_usage` | Gauge | CPU 使用 | % |
| `milvus_disk_usage_bytes` | Gauge | 磁盘使用 | bytes |

#### 服务状态指标

| 指标名 | 类型 | 说明 |
|-------|-----|------|
| `up{job="milvus"}` | Gauge | 服务存活状态（1=正常，0=异常） |
| `milvus_service_health` | Gauge | 服务健康状态 |
| `milvus_service_version` | Gauge | 服务版本 |

#### 索引指标

| 指标名 | 类型 | 说明 |
|-------|-----|------|
| `milvus_index_building_count` | Gauge | 正在构建的索引数 |
| `milvus_index_loaded_count` | Gauge | 已加载的索引数 |
| `milvus_index_size_bytes` | Gauge | 索引大小 |
| `milvus_index_build_latency_seconds` | Histogram | 索引构建延迟 |

---

## 三、告警规则

### 3.1 告警规则配置

**配置示例（alert_rules.yml）**：

```yaml
groups:
  - name: milvus_alerts
    interval: 90s
    rules:
      # 服务宕机告警
      - alert: MilvusServiceDown
        expr: up{job="milvus"} == 0
        for: 1m
        labels:
          severity: critical
          service: milvus
        annotations:
          summary: "Milvus 服务宕机"
          description: "Milvus 服务已停止运行超过 1 分钟"

      # 查询延迟过高
      - alert: MilvusHighQueryLatency
        expr: avg_over_time(milvus_query_latency_seconds_p95[5m]) > 0.1
        for: 2m
        labels:
          severity: warning
          service: milvus
        annotations:
          summary: "Milvus 查询延迟过高"
          description: "P95 查询延迟超过 100ms，当前值: {{ $value }}s"

      # 内存使用率过高
      - alert: MilvusHighMemoryUsage
        expr: milvus_memory_usage_bytes / milvus_memory_limit_bytes > 0.85
        for: 5m
        labels:
          severity: warning
          service: milvus
        annotations:
          summary: "Milvus 内存使用率过高"
          description: "内存使用率超过 85%，当前值: {{ humanizePercent $value }}"

      # 查询成功率下降
      - alert: MilvusLowQuerySuccessRate
        expr: avg_over_time(milvus_query_success_rate[5m]) < 0.99
        for: 2m
        labels:
          severity: critical
          service: milvus
        annotations:
          summary: "Milvus 查询成功率下降"
          description: "查询成功率低于 99%，当前值: {{ $value }}"
```

---

### 3.2 告警规则详解

#### 服务可用性告警

| 告警名称 | 表达式 | 阈值 | 级别 |
|---------|-------|-----|-----|
| MilvusServiceDown | `up{job="milvus"} == 0` | 持续 1min | critical |
| MilvusHealthCheckFailed | `milvus_service_health == 0` | 持续 1min | critical |

#### 性能告警

| 告警名称 | 表达式 | 阈值 | 级别 |
|---------|-------|-----|-----|
| MilvusHighQueryLatency | `avg_over_time(milvus_query_latency_seconds_p95[5m]) > 0.1` | >100ms | warning |
| MilvusHighInsertLatency | `avg_over_time(milvus_insert_latency_seconds_p95[5m]) > 1` | >1s | warning |
| MilvusLowQuerySuccessRate | `avg_over_time(milvus_query_success_rate[5m]) < 0.99` | <99% | critical |
| MilvusLowInsertSuccessRate | `avg_over_time(milvus_insert_success_rate[5m]) < 0.99` | <99% | critical |

#### 资源告警

| 告警名称 | 表达式 | 阈值 | 级别 |
|---------|-------|-----|-----|
| MilvusHighMemoryUsage | `milvus_memory_usage_bytes / milvus_memory_limit_bytes > 0.85` | >85% | warning |
| MilvusHighMemoryUsageCritical | `milvus_memory_usage_bytes / milvus_memory_limit_bytes > 0.95` | >95% | critical |
| MilvusHighCPUUsage | `avg_over_time(milvus_cpu_usage[5m]) > 0.8` | >80% | warning |

#### 索引告警

| 告警名称 | 表达式 | 阈值 | 级别 |
|---------|-------|-----|-----|
| MilvusIndexBuildTimeout | `milvus_index_build_latency_seconds > 300` | >5min | warning |
| MilvusIndexNotLoaded | `milvus_index_loaded_count == 0` | 持续 5min | warning |

---

### 3.3 告警级别定义

| 级别 | 含义 | 响应时间 | 处理方式 |
|-----|------|---------|---------|
| **critical** | 服务不可用 | 立即 | 通知值班人员，立即处理 |
| **warning** | 性能下降或资源紧张 | 15分钟内 | 安排时间处理 |
| **info** | 信息性通知 | 按需 | 记录日志，定期查看 |

---

## 四、告警通知

### 4.1 Alertmanager 配置

**配置示例（alertmanager.yml）**：

```yaml
global:
  resolve_timeout: 5m

route:
  group_by: ['alertname', 'severity']
  group_wait: 30s
  group_interval: 5m
  repeat_interval: 1h
  receiver: 'webhook'

receivers:
  - name: 'webhook'
    webhook_configs:
      - url: 'https://your-webhook-url.com/alert'
        send_resolved: true

  - name: 'email'
    email_configs:
      - to: 'admin@example.com'
        send_resolved: true

inhibit_rules:
  - source_match:
      severity: 'critical'
    target_match:
      severity: 'warning'
    equal: ['alertname']
```

**关键配置说明**：
- `group_wait`：同一组告警等待时间（建议 30s）
- `group_interval`：同一组告警间隔（建议 5min）
- `repeat_interval`：重复通知间隔（建议 1h）
- `inhibit_rules`：抑制规则，避免重复告警

---

### 4.2 通知渠道

| 渠道 | 适用场景 | 配置复杂度 |
|-----|---------|-----------|
| **Webhook** | 集成自定义系统 | 中等 |
| **Email** | 邮件通知 | 简单 |
| **Slack** | 团队协作通知 | 简单 |
| **钉钉/企业微信** | 企业内部通知 | 中等 |
| **短信/电话** | 紧急告警 | 复杂 |

---

## 五、可视化仪表盘

### 5.1 Grafana 仪表盘配置

**仪表盘结构**：

#### 概览面板
- 服务状态指示灯
- 查询/插入 QPS
- 内存/CPU 使用
- 告警统计

#### 查询性能面板
- 查询 QPS 趋势
- 查询延迟分布（P50/P90/P95/P99）
- 查询成功率
- TopK 分布

#### 插入性能面板
- 插入 QPS 趋势
- 插入延迟分布
- 插入成功率
- 批量大小分布

#### 资源使用面板
- 内存使用趋势
- CPU 使用趋势
- 磁盘使用趋势
- 网络流量

#### 索引状态面板
- 索引构建状态
- 索引加载状态
- 索引大小统计

---

### 5.2 推荐图表

```json
{
  "panels": [
    {
      "title": "查询 QPS",
      "type": "graph",
      "targets": [
        { "expr": "rate(milvus_query_qps[1m])", "legendFormat": "QPS" }
      ],
      "yAxes": [{ "label": "ops/s" }]
    },
    {
      "title": "查询延迟 P95",
      "type": "graph",
      "targets": [
        { "expr": "avg_over_time(milvus_query_latency_seconds_p95[5m])", "legendFormat": "P95" }
      ],
      "yAxes": [{ "label": "seconds" }]
    },
    {
      "title": "内存使用率",
      "type": "gauge",
      "targets": [
        { "expr": "milvus_memory_usage_bytes / milvus_memory_limit_bytes * 100" }
      ],
      "min": 0, "max": 100,
      "thresholds": "80,90"
    }
  ]
}
```

---

## 六、监控最佳实践

### 6.1 指标采集优化

**过滤不必要的指标**：
```yaml
relabel_configs:
  - source_labels: [__name__]
    regex: '^(milvus_query_.*|milvus_insert_.*|milvus_memory_.*|up)$'
    action: keep
```

**设置合理的采集间隔**：
- 生产环境：60-90s
- 测试环境：30-60s
- 避免过短的采集间隔导致资源浪费

---

### 6.2 告警规则管理

**告警抑制**：
```yaml
inhibit_rules:
  - source_match:
      severity: 'critical'
    target_match:
      severity: 'warning'
    equal: ['alertname', 'instance']
```

**告警静默**：
```yaml
time_intervals:
  - name: 'work_hours'
    time_start: '09:00'
    time_end: '18:00'
    days: ['monday', 'tuesday', 'wednesday', 'thursday', 'friday']
```

---

### 6.3 数据保留策略

**Prometheus 数据保留**：
```bash
docker run -d \
  -p 9090:9090 \
  -v /data/prometheus:/prometheus \
  prom/prometheus \
  --storage.tsdb.retention.time=30d
```

**建议保留时间**：
- 开发环境：7-14天
- 测试环境：14-30天
- 生产环境：30-90天

---

### 6.4 监控安全

**限制访问**：
```yaml
# nginx.conf
server {
  listen 9090;
  location / {
    proxy_pass http://localhost:9090;
    auth_basic "Prometheus";
    auth_basic_user_file /etc/nginx/.htpasswd;
  }
}
```

**加密传输**：
- 使用 HTTPS 传输 metrics
- 配置 TLS 证书
- 启用双向认证（可选）

---

## 七、故障排查流程

### 7.1 服务宕机排查

```
1. 检查容器状态
   docker-compose ps

2. 查看服务日志
   docker-compose logs standalone

3. 检查依赖服务
   docker-compose ps etcd minio

4. 检查端口监听
   netstat -tlnp | grep 19530

5. 检查资源使用
   free -h && df -h

6. 检查系统日志
   dmesg | grep -i oom
```

### 7.2 性能问题排查

```
1. 查看实时指标
   curl http://localhost:9091/metrics

2. 检查查询延迟
   avg_over_time(milvus_query_latency_seconds_p95[5m])

3. 检查资源瓶颈
   docker stats

4. 检查索引状态
   milvus_index_loaded_count

5. 分析慢查询
   查看查询日志，识别慢查询

6. 优化配置
   调整 nprobe、批量大小等参数
```

---

## 八、监控维护

### 8.1 定期维护任务

| 任务 | 频率 | 说明 |
|-----|-----|------|
| 清理旧数据 | 每周 | 删除过期的监控数据 |
| 检查告警规则 | 每月 | 评估规则有效性 |
| 更新仪表盘 | 季度 | 根据业务调整 |
| 性能基线评估 | 季度 | 建立新的性能基准 |

### 8.2 监控升级

**升级步骤**：
1. 备份当前配置
2. 升级 Prometheus 版本
3. 更新 Milvus metrics 配置
4. 验证指标采集
5. 测试告警规则

---

## 附录：常用监控查询

```promql
# 查询 QPS
rate(milvus_query_qps[1m])

# 查询延迟 P95
avg_over_time(milvus_query_latency_seconds_p95[5m])

# 内存使用率
milvus_memory_usage_bytes / milvus_memory_limit_bytes * 100

# 查询成功率
avg_over_time(milvus_query_success_rate[5m])

# 服务可用性
sum(up{job="milvus"}) / count(up{job="milvus"}) * 100

# 索引加载数量
milvus_index_loaded_count

# 插入延迟
avg_over_time(milvus_insert_latency_seconds_p95[5m])
```