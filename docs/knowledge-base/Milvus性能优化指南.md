# Milvus 性能优化指南

## 一、索引优化

### 1.1 索引类型选择

| 索引类型 | 适用场景 | 查询速度 | 构建速度 | 内存占用 |
|---------|---------|---------|---------|---------|
| **FLAT** | 小数据集（<10万） | 慢 | 快 | 低 |
| **IVF_FLAT** | 中等数据集 | 中 | 中 | 中 |
| **IVF_SQ8** | 大规模数据集 | 快 | 中 | 低 |
| **IVF_PQ** | 超大规模数据集 | 快 | 慢 | 很低 |
| **ANNOY** | 高召回率需求 | 中 | 慢 | 中 |

**选择建议**：
- 数据量 < 10万：FLAT 或 IVF_FLAT
- 数据量 10万-1000万：IVF_SQ8
- 数据量 > 1000万：IVF_PQ

---

### 1.2 索引参数优化

#### IVF 系列索引

**nlist 参数**：
- 建议值：数据集大小的平方根
- 例如：100万数据 → nlist = 1000
- 调优原则：nlist 越大，查询越精确但速度越慢

**nprobe 参数**：
- 查询时使用，建议值：nlist * 0.1 ~ nlist * 0.2
- 例如：nlist=1000 → nprobe=100~200
- 调优原则：nprobe 越大，召回率越高但速度越慢

**配置示例**：
```python
from pymilvus import Collection, IndexType, MetricType

# 创建 IVF_SQ8 索引
collection.create_index(
    field_name="embedding",
    index_params={
        "index_type": "IVF_SQ8",
        "metric_type": "L2",
        "params": {"nlist": 1000}
    }
)

# 查询时设置 nprobe
results = collection.search(
    data=query_vectors,
    anns_field="embedding",
    param={"nprobe": 200},
    limit=10
)
```

---

## 二、查询优化

### 2.1 批量查询优化

**最佳实践**：
- 单次查询向量数量：10-100 条
- 避免单条查询，使用批量查询
- 设置合理的 topk 值（不要过大）

**对比示例**：
```python
# 低效：循环单条查询
for vector in vectors:
    result = collection.search([vector], ...)

# 高效：批量查询
results = collection.search(vectors, ...)
```

---

### 2.2 连接池优化

```python
from pymilvus import connections

# 配置连接池
connections.connect(
    alias="default",
    host="localhost",
    port="19530",
    pool_size=10,
    max_waiting_in_pool=5
)
```

**连接池参数**：
- `pool_size`：最大连接数（建议 5-20）
- `max_waiting_in_pool`：最大等待连接数

---

### 2.3 查询缓存

Milvus 支持查询结果缓存，可显著提升重复查询性能：

```python
# 启用缓存
collection.load()

# 查询会自动使用缓存
results = collection.search(...)
```

**缓存策略**：
- 对于频繁查询的热点数据，保持集合加载状态
- 定期清理不再使用的缓存

---

## 三、插入优化

### 3.1 批量插入

**最佳批量大小**：1000-5000 条/批

```python
# 高效批量插入
batch_size = 2000
for i in range(0, total_count, batch_size):
    batch = data[i:i+batch_size]
    collection.insert(batch)
```

**批量大小选择依据**：
- 小批量（<1000）：网络开销大
- 大批量（>10000）：内存压力大
- 建议：根据向量维度和内存调整

---

### 3.2 异步插入

对于非实时场景，可使用异步插入：

```python
# 异步插入
future = collection.insert(data, _async=True)
# 后续处理...
result = future.result()  # 获取结果
```

---

### 3.3 索引构建时机

**策略对比**：

| 策略 | 适用场景 | 优点 | 缺点 |
|-----|---------|-----|-----|
| **先插入后建索引** | 全量数据导入 | 构建效率高 | 查询不可用 |
| **插入时自动建索引** | 增量数据 | 查询可用 | 插入速度慢 |
| **定时批量建索引** | 混合场景 | 平衡性能 | 实现复杂 |

---

## 四、资源优化

### 4.1 内存配置

**Milvus 内存分配**：
- 索引内存：约为索引文件大小的 1.5-2 倍
- 查询缓存：根据并发量调整
- 建议：至少保留 20% 内存给系统

**配置示例（docker-compose.yml）**：
```yaml
services:
  standalone:
    deploy:
      resources:
        limits:
          memory: 4g
        reservations:
          memory: 2g
```

---

### 4.2 CPU 配置

**CPU 核心数建议**：
- 单节点：4-8 核
- 查询密集型：增加 CPU 核数
- 插入密集型：适当降低 CPU（瓶颈在 IO）

**绑核优化**：
```yaml
services:
  standalone:
    deploy:
      resources:
        limits:
          cpus: '4.0'
```

---

### 4.3 存储优化

**存储类型选择**：
- **SSD**：推荐用于生产环境，IOPS > 1000
- **NVMe SSD**：高性能场景，IOPS > 10000
- **HDD**：仅用于归档或冷数据

**挂载优化**：
```bash
# 检查磁盘 IO
iostat -x 1 5

# 优化挂载参数
# /etc/fstab
/dev/sda1 /data ext4 defaults,noatime,nodiratime 0 2
```

---

## 五、网络优化

### 5.1 连接方式选择

| 方式 | 适用场景 | 优点 | 缺点 |
|-----|---------|-----|-----|
| **gRPC** | 生产环境 | 高性能、低延迟 | 配置复杂 |
| **HTTP** | 调试、跨语言 | 简单、通用 | 性能较低 |

---

### 5.2 网络参数调整

```python
from pymilvus import connections, ConnectParam

conn_param = ConnectParam.new_builder()\
    .with_host("localhost")\
    .with_port("19530")\
    .with_connect_timeout(30000)\
    .with_idle_timeout(60000)\
    .build()

connections.connect(
    alias="default",
    conn_param=conn_param
)
```

**超时参数建议**：
- `connect_timeout`：30-60 秒
- `idle_timeout`：60-300 秒

---

### 5.3 内网部署

对于高要求场景，建议：
- Milvus 与客户端部署在同一内网
- 使用高速网络（10Gbps+）
- 避免跨区域访问

---

## 六、配置优化

### 6.1 Milvus 配置文件

**关键配置项**：

```yaml
# milvus.yaml
server:
  query:
    cache:
      enabled: true
      memory_limit: 2GB
  index:
    build:
      num_threads: 4
storage:
  primary_path: /var/lib/milvus
  secondary_path: /mnt/ssd/milvus
```

**内存配置**：
- `memory_limit`：查询缓存上限
- `num_threads`：索引构建线程数

---

### 6.2 Docker Compose 优化

```yaml
version: "3.8"
services:
  standalone:
    image: milvusdb/milvus:v2.6.13
    command: ["milvus", "run", "standalone"]
    environment:
      MILVUS_LOG_LEVEL: warn
      MILVUS_QUERY_CACHE_ENABLE: "true"
      MILVUS_QUERY_CACHE_MEMORY_LIMIT: 2GB
    volumes:
      - ./volumes/milvus:/var/lib/milvus
    ports:
      - "19530:19530"
    deploy:
      resources:
        limits:
          memory: 4g
          cpus: '4.0'
        reservations:
          memory: 2g
```

---

## 七、监控与调优

### 7.1 关键监控指标

| 指标 | 说明 | 告警阈值 |
|-----|------|---------|
| `milvus_query_latency_seconds` | 查询延迟 | > 0.1s |
| `milvus_insert_latency_seconds` | 插入延迟 | > 1s |
| `milvus_memory_usage_bytes` | 内存使用 | > 80% |
| `milvus_query_qps` | 查询 QPS | 根据业务 |
| `milvus_insert_qps` | 插入 QPS | 根据业务 |
| `up{job="milvus"}` | 服务状态 | = 0 |

---

### 7.2 性能测试工具

```bash
# 使用 Milvus Benchmark
pip install milvus-benchmark

# 运行性能测试
milvus_benchmark --host localhost --port 19530 \
  --collection-name test_collection \
  --vector-count 1000000 \
  --dim 768
```

---

### 7.3 性能分析流程

1. **建立基准**：记录当前性能指标
2. **识别瓶颈**：通过监控定位瓶颈（CPU/内存/IO/网络）
3. **优化调整**：针对性优化配置
4. **验证效果**：重新测试，对比基准
5. **持续监控**：设置告警，及时发现问题

---

## 八、进阶优化

### 8.1 分布式部署

对于超大规模数据（>1亿向量），建议使用分布式部署：

```yaml
# 分布式配置示例
cluster:
  enabled: true
  role: querynode/indexnode/datanode
```

**分布式优势**：
- 水平扩展能力
- 更高的可用性
- 支持更大数据量

---

### 8.2 混合存储策略

```yaml
storage:
  primary_path: /ssd/milvus  # 热数据
  secondary_path: /hdd/milvus  # 冷数据
```

**策略**：
- 频繁访问的数据放在 SSD
- 归档数据放在 HDD
- 定期迁移冷热数据

---

### 8.3 查询预热

```python
# 预热查询缓存
warmup_queries = generate_warmup_queries()
for query in warmup_queries:
    collection.search([query], limit=10)
```

**预热时机**：
- 服务启动后
- 索引重建后
- 业务低峰期

---

## 九、优化效果评估

### 9.1 性能指标对比

| 指标 | 优化前 | 优化后 | 提升 |
|-----|-------|-------|-----|
| 查询延迟 | 200ms | 50ms | 75% |
| 插入速度 | 500条/秒 | 5000条/秒 | 900% |
| 内存占用 | 8GB | 4GB | 50% |

### 9.2 成本效益分析

- **硬件成本**：升级配置 vs 优化效果
- **人力成本**：优化投入 vs 维护成本
- **业务收益**：性能提升 vs 用户体验

---

## 十、常见问题与解决方案

### 10.1 查询慢但内存充足

**原因**：索引参数不合理

**解决**：
- 增加 nprobe 提高查询速度（牺牲召回率）
- 重建索引，调整 nlist

### 10.2 插入慢

**原因**：批量太小或存储 IO 慢

**解决**：
- 增大批量大小
- 使用 SSD 存储
- 异步插入

### 10.3 内存溢出

**原因**：索引过大或缓存过多

**解决**：
- 限制内存使用
- 使用量化索引（IVF_SQ8/IVF_PQ）
- 定期释放缓存

---

## 附录：性能优化检查清单

- [ ] 选择合适的索引类型
- [ ] 优化索引参数（nlist, nprobe）
- [ ] 使用批量查询/插入
- [ ] 配置连接池
- [ ] 使用 SSD 存储
- [ ] 合理分配资源
- [ ] 启用查询缓存
- [ ] 监控关键指标
- [ ] 定期性能测试
- [ ] 建立优化反馈机制