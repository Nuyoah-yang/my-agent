# Milvus 常见问题排查指南

## 一、连接问题

### 1.1 客户端无法连接 Milvus

**现象**：客户端报 "connection refused" 或 "timeout" 错误

**可能原因**：
- Milvus 服务未启动
- 网络防火墙阻止了端口访问
- 配置的 IP/端口不正确

**排查步骤**：
```bash
# 检查 Milvus 容器状态
docker-compose ps

# 检查端口是否监听
netstat -tlnp | grep 19530

# 测试端口连通性
telnet localhost 19530

# 查看 Milvus 日志
docker-compose logs standalone
```

**解决方案**：
- 启动 Milvus 服务：`docker-compose up -d`
- 在防火墙/安全组开放 19530 端口
- 确认配置文件中的 host 和 port 正确

---

### 1.2 连接超时

**现象**：客户端连接时出现 "context deadline exceeded"

**可能原因**：
- 网络延迟过高
- Milvus 服务过载
- 连接超时时间设置过短

**排查步骤**：
```bash
# 检查网络延迟
ping <milvus_host>

# 检查 Milvus CPU/内存使用
docker stats

# 检查系统负载
uptime
```

**解决方案**：
- 增加连接超时时间（建议 30-60 秒）
- 优化 Milvus 配置，增加资源限制
- 考虑升级服务器配置

---

## 二、性能问题

### 2.1 查询速度慢

**现象**：向量检索延迟超过 100ms

**可能原因**：
- 索引类型选择不当
- 索引参数配置不合理
- 数据量过大，未分片
- 内存不足导致 Swap

**排查步骤**：
```bash
# 查看索引状态
curl http://localhost:9091/metrics | grep milvus_index

# 检查内存使用
free -h

# 检查 Swap 使用
vmstat 1 5
```

**解决方案**：
- 选择合适的索引类型（IVF_FLAT/IVF_SQ8/ANNOY）
- 调整 nlist 参数（建议为数据量的平方根）
- 增加系统内存，避免 Swap
- 考虑使用分布式部署

---

### 2.2 插入速度慢

**现象**：批量插入速度低于 1000 条/秒

**可能原因**：
- 批量大小设置不合理
- 网络带宽限制
- MinIO 存储性能瓶颈
- etcd 性能问题

**排查步骤**：
```bash
# 检查 MinIO 状态
curl http://localhost:9000/minio/health/live

# 检查 etcd 健康状态
docker exec milvus-etcd etcdctl endpoint health

# 查看磁盘 IO
iostat -x 1 5
```

**解决方案**：
- 调整 batch size（建议 1000-5000）
- 使用本地 SSD 存储
- 增加 MinIO 资源限制
- 优化 etcd 配置

---

### 2.3 内存占用过高

**现象**：Milvus 内存占用持续增长

**可能原因**：
- 索引加载到内存
- 查询缓存过大
- 内存泄漏

**排查步骤**：
```bash
# 查看 Milvus 内存使用
docker stats milvus-standalone

# 检查索引大小
curl http://localhost:9091/metrics | grep milvus_index_size

# 查看内存相关指标
curl http://localhost:9091/metrics | grep milvus_memory
```

**解决方案**：
- 释放未使用的索引
- 调整 cache 配置
- 定期重启服务
- 增加内存或升级服务器

---

## 三、数据问题

### 3.1 数据插入失败

**现象**：插入数据时报错 "insert failed"

**可能原因**：
- 向量维度不匹配
- 数据格式错误
- 集合不存在
- 权限不足

**排查步骤**：
```bash
# 查看集合信息
curl -X POST http://localhost:9091/api/v1/collection/describe \
  -H "Content-Type: application/json" \
  -d '{"collection_name": "your_collection"}'

# 检查向量维度是否一致
```

**解决方案**：
- 确认向量维度与集合定义一致
- 检查数据格式是否正确
- 确认集合已创建
- 检查权限配置

---

### 3.2 查询结果为空

**现象**：查询返回空结果

**可能原因**：
- 查询参数设置不当
- 数据未正确插入
- 索引未建立或未加载

**排查步骤**：
```bash
# 检查集合统计信息
curl -X POST http://localhost:9091/api/v1/collection/statistics \
  -H "Content-Type: application/json" \
  -d '{"collection_name": "your_collection"}'

# 检查索引状态
curl -X POST http://localhost:9091/api/v1/index/describe \
  -H "Content-Type: application/json" \
  -d '{"collection_name": "your_collection"}'
```

**解决方案**：
- 调整查询参数（nprobe, topk）
- 确认数据已正确插入
- 确保索引已建立并加载

---

### 3.3 数据丢失

**现象**：查询时发现部分数据缺失

**可能原因**：
- 插入未成功但未报错
- 数据过期被清理
- 存储故障

**排查步骤**：
```bash
# 检查 MinIO 数据完整性
docker exec milvus-minio mc ls myminio/milvus

# 检查插入日志
docker-compose logs standalone | grep -i insert

# 检查 etcd 数据
docker exec milvus-etcd etcdctl get --prefix milvus
```

**解决方案**：
- 启用插入确认机制
- 定期备份数据
- 检查存储健康状态
- 实现数据校验机制

---

## 四、服务稳定性问题

### 4.1 Milvus 服务频繁重启

**现象**：容器自动重启

**可能原因**：
- 内存不足被 OOM Killer 杀死
- 配置错误导致崩溃
- 依赖服务故障

**排查步骤**：
```bash
# 查看系统日志
dmesg | grep -i oom

# 查看容器重启次数
docker inspect milvus-standalone | grep RestartCount

# 查看最近崩溃日志
docker-compose logs --tail=100 standalone
```

**解决方案**：
- 增加系统内存或启用 Swap
- 检查并修复配置错误
- 确保依赖服务（etcd, MinIO）正常运行
- 配置容器重启策略

---

### 4.2 服务无响应

**现象**：Milvus 服务不响应请求

**可能原因**：
- 死锁或长时间阻塞
- 资源耗尽
- 网络分区

**排查步骤**：
```bash
# 检查服务健康状态
curl http://localhost:9091/healthz

# 查看进程状态
docker top milvus-standalone

# 检查网络连接
netstat -an | grep 19530
```

**解决方案**：
- 重启 Milvus 服务
- 排查死锁原因
- 增加资源限制
- 检查网络配置

---

## 五、监控告警问题

### 5.1 告警规则不触发

**现象**：预期的告警未触发

**可能原因**：
- 指标数据未采集
- 阈值设置不合理
- 规则配置错误

**排查步骤**：
```bash
# 检查指标是否采集
curl http://localhost:9090/api/v1/query \
  --data-urlencode 'query=up{job="milvus"}'

# 检查规则状态
curl http://localhost:9090/api/v1/rules
```

**解决方案**：
- 确保 Prometheus 正确采集指标
- 调整告警阈值
- 检查规则表达式

---

### 5.2 告警频繁触发

**现象**：告警频繁触发，产生大量通知

**可能原因**：
- 阈值设置过严
- 抖动导致误报
- 缺乏静默机制

**解决方案**：
- 调整阈值和 for 持续时间
- 添加告警静默规则
- 优化监控策略

---

## 六、常用排查命令汇总

```bash
# 1. 查看 Milvus 状态
docker-compose ps

# 2. 查看 Milvus 日志
docker-compose logs -f standalone

# 3. 检查端口监听
netstat -tlnp | grep -E '19530|9091'

# 4. 检查资源使用
docker stats

# 5. 检查内存
free -h

# 6. 检查磁盘
df -h

# 7. 检查网络连通性
telnet localhost 19530
curl http://localhost:9091/healthz

# 8. 查看指标
curl http://localhost:9091/metrics

# 9. 查看集合列表
curl -X GET http://localhost:9091/api/v1/collections

# 10. 查看系统信息
curl http://localhost:9091/api/v1/systeminfo
```

---

## 七、紧急处理流程

### 7.1 Milvus 服务宕机

1. **确认状态**：`docker-compose ps`
2. **查看日志**：`docker-compose logs standalone`
3. **尝试重启**：`docker-compose restart standalone`
4. **检查依赖**：确认 etcd 和 MinIO 正常
5. **恢复数据**：如果需要，从备份恢复

### 7.2 内存不足告警

1. **查看内存使用**：`free -h`
2. **检查进程**：`docker stats`
3. **释放资源**：清理缓存、释放未使用索引
4. **临时扩容**：启用 Swap
5. **长期优化**：升级服务器或优化配置

### 7.3 数据丢失

1. **确认范围**：检查丢失的数据量
2. **检查日志**：查找错误原因
3. **从备份恢复**：使用最近备份恢复
4. **验证完整性**：核对数据数量和内容
5. **预防措施**：增加备份频率，启用校验机制