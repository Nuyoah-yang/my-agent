# 监控中心（Prometheus）最小方案

本目录用于搭建一个最小可用的监控中心，让 AIOps 能读取真实告警并模拟生产场景。

## 1. 目录说明

- `docker-compose.monitoring.yml`：启动 Prometheus
- `prometheus.yml`：抓取配置（含 Milvus 目标）
- `alert_rules.yml`：告警规则示例

## 2. 启动步骤

1. 修改 `prometheus.yml` 中 Milvus 目标地址：
   - `192.168.56.101:9091` 替换为你虚拟机中 Milvus metrics 的实际地址和端口。
2. 在当前目录执行：
   - `docker compose -f docker-compose.monitoring.yml up -d`
3. 浏览器访问：
   - `http://localhost:9090`
4. 在 Prometheus 页面验证：
   - `Status -> Targets`，确认 `milvus` 目标为 `UP`
   - `Alerts` 页面查看 `MilvusTargetDown` / `MilvusHighScrapeDuration`

## 3. 后端联调参数

将后端 `application.yml` 配置为：

```yaml
prometheus:
  base-url: http://localhost:9090
  timeout: 10
  mock-enabled: false
```

如果后端不在同一台机器，请把 `base-url` 改成 Prometheus 的可访问地址（例如 `http://<vm-ip>:9090`）。

## 4. 故障演练（推荐）

你可以临时关闭 Milvus 容器，验证 AIOps 是否能感知告警：

1. 停 Milvus（或断开网络）
2. 等待约 1 分钟
3. Prometheus `Alerts` 出现 `MilvusTargetDown`
4. 调用 `POST /api/chat/ai_ops`，观察报告是否包含“监控目标不可达”分析

