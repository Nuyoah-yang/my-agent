# Milvus 部署与配置指南

## 一、环境准备

### 1.1 硬件要求

| 部署模式 | CPU | 内存 | 存储 | 网络 |
|---------|-----|------|------|------|
| **Standalone** | 4核+ | 8GB+ | 100GB+ SSD | 1Gbps+ |
| **Cluster** | 8核+ | 16GB+ | 500GB+ SSD | 10Gbps+ |

**推荐配置**：
- 生产环境：8核16GB内存，500GB SSD
- 开发环境：4核8GB内存，100GB SSD

---

### 1.2 软件要求

| 软件 | 版本 | 说明 |
|-----|------|-----|
| **Docker** | 20.10+ | 容器运行环境 |
| **Docker Compose** | 2.0+ | 容器编排工具 |
| **Linux** | CentOS 7+/Ubuntu 18.04+ | 操作系统 |

---

### 1.3 端口规划

| 端口 | 服务 | 用途 |
|-----|------|-----|
| **19530** | Milvus gRPC | 客户端连接 |
| **9091** | Milvus HTTP | Metrics 和健康检查 |
| **2379** | etcd | 元数据存储 |
| **9000** | MinIO | 对象存储 API |
| **9001** | MinIO | Web 控制台 |

---

## 二、Standalone 部署

### 2.1 快速部署

```bash
# 创建部署目录
mkdir -p /data/milvus/{config,data/etcd,data/minio,data/milvus,logs}
cd /data/milvus/config

# 创建 docker-compose.yml
cat > docker-compose.yml << 'EOF'
version: "3.8"

services:
  etcd:
    container_name: milvus-etcd
    image: quay.io/coreos/etcd:v3.5.25
    environment:
      - ETCD_AUTO_COMPACTION_MODE=revision
      - ETCD_AUTO_COMPACTION_RETENTION=1000
      - ETCD_QUOTA_BACKEND_BYTES=4294967296
      - ETCD_SNAPSHOT_COUNT=50000
    volumes:
      - ../data/etcd:/etcd
    command: >
      etcd
      -advertise-client-urls=http://127.0.0.1:2379
      -listen-client-urls=http://0.0.0.0:2379
      --data-dir /etcd
    healthcheck:
      test: ["CMD", "etcdctl", "endpoint", "health"]
      interval: 30s
      timeout: 20s
      retries: 3
    restart: unless-stopped
    networks:
      - milvus

  minio:
    container_name: milvus-minio
    image: minio/minio:RELEASE.2024-12-18T13-15-44Z
    environment:
      MINIO_ACCESS_KEY: minioadmin
      MINIO_SECRET_KEY: minioadmin
    volumes:
      - ../data/minio:/minio_data
    command: minio server /minio_data --console-address ":9001"
    ports:
      - "9000:9000"
      - "9001:9001"
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:9000/minio/health/live"]
      interval: 30s
      timeout: 20s
      retries: 3
    restart: unless-stopped
    networks:
      - milvus

  standalone:
    container_name: milvus-standalone
    image: milvusdb/milvus:v2.6.13
    command: ["milvus", "run", "standalone"]
    environment:
      ETCD_ENDPOINTS: etcd:2379
      MINIO_ADDRESS: minio:9000
    volumes:
      - ../data/milvus:/var/lib/milvus
    ports:
      - "19530:19530"
      - "9091:9091"
    depends_on:
      - etcd
      - minio
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:9091/healthz"]
      interval: 30s
      start_period: 90s
      timeout: 20s
      retries: 5
    restart: unless-stopped
    networks:
      - milvus
    deploy:
      resources:
        limits:
          memory: 8g
          cpus: '4.0'
        reservations:
          memory: 4g

networks:
  milvus:
    driver: bridge
EOF

# 启动服务
docker-compose up -d

# 查看状态
docker-compose ps
```

---

### 2.2 验证部署

```bash
# 检查服务状态
docker-compose ps

# 检查健康状态
curl http://localhost:9091/healthz

# 查看日志
docker-compose logs -f standalone

# 测试连接
python3 -c "
from pymilvus import connections
connections.connect(host='localhost', port='19530')
print('Milvus connection successful!')
"
```

---

## 三、配置文件详解

### 3.1 Milvus 配置结构

```yaml
# milvus.yaml
server:
  port: 19530
  grpc:
    max_send_msg_size: 1024
    max_recv_msg_size: 1024

query:
  cache:
    enabled: true
    memory_limit: 4GB
  gpu:
    enabled: false

index:
  build:
    num_threads: 4
    memory_limit: 8GB

storage:
  primary_path: /var/lib/milvus
  secondary_path: /data/milvus/secondary
```

---

### 3.2 关键配置项

#### 服务器配置

| 配置项 | 默认值 | 说明 |
|-------|-------|------|
| `server.port` | 19530 | gRPC 端口 |
| `server.grpc.max_send_msg_size` | 1024 | 最大发送消息大小(MB) |
| `server.grpc.max_recv_msg_size` | 1024 | 最大接收消息大小(MB) |

#### 查询配置

| 配置项 | 默认值 | 说明 |
|-------|-------|------|
| `query.cache.enabled` | true | 是否启用查询缓存 |
| `query.cache.memory_limit` | 4GB | 缓存内存上限 |
| `query.gpu.enabled` | false | 是否启用 GPU 加速 |

#### 索引配置

| 配置项 | 默认值 | 说明 |
|-------|-------|------|
| `index.build.num_threads` | 4 | 索引构建线程数 |
| `index.build.memory_limit` | 8GB | 索引构建内存上限 |

#### 存储配置

| 配置项 | 默认值 | 说明 |
|-------|-------|------|
| `storage.primary_path` | /var/lib/milvus | 主存储路径 |
| `storage.secondary_path` | - | 次存储路径（冷数据） |

---

### 3.3 环境变量配置

**通过环境变量覆盖配置**：

```bash
docker run -d \
  -e MILVUS_SERVER_PORT=19530 \
  -e MILVUS_QUERY_CACHE_ENABLE=true \
  -e MILVUS_QUERY_CACHE_MEMORY_LIMIT=4GB \
  milvusdb/milvus:v2.6.13
```

**常用环境变量**：

| 环境变量 | 对应配置 | 说明 |
|---------|---------|------|
| `MILVUS_SERVER_PORT` | server.port | gRPC 端口 |
| `MILVUS_QUERY_CACHE_ENABLE` | query.cache.enabled | 缓存开关 |
| `MILVUS_QUERY_CACHE_MEMORY_LIMIT` | query.cache.memory_limit | 缓存上限 |
| `MILVUS_INDEX_BUILD_NUM_THREADS` | index.build.num_threads | 索引线程数 |
| `MILVUS_LOG_LEVEL` | log.level | 日志级别 |

---

## 四、网络配置

### 4.1 端口映射

```yaml
services:
  standalone:
    ports:
      - "19530:19530"  # gRPC 端口
      - "9091:9091"    # HTTP/Metrics 端口
```

### 4.2 防火墙配置

```bash
# 开放端口
firewall-cmd --add-port=19530/tcp --permanent
firewall-cmd --add-port=9091/tcp --permanent
firewall-cmd --reload

# 查看已开放端口
firewall-cmd --list-ports
```

### 4.3 阿里云安全组配置

| 端口 | 协议 | 授权对象 | 说明 |
|-----|------|---------|------|
| 19530 | TCP | 0.0.0.0/0 | Milvus gRPC |
| 9091 | TCP | 0.0.0.0/0 | Metrics/健康检查 |
| 9090 | TCP | 0.0.0.0/0 | Prometheus（可选） |

---

## 五、资源限制

### 5.1 Docker 资源限制

```yaml
services:
  standalone:
    deploy:
      resources:
        limits:
          memory: 8g
          cpus: '4.0'
        reservations:
          memory: 4g
          cpus: '2.0'
```

**资源限制说明**：
- `limits.memory`：最大可用内存
- `limits.cpus`：最大可用 CPU 核数
- `reservations.memory`：预留内存
- `reservations.cpus`：预留 CPU 核数

---

### 5.2 内存优化建议

| 场景 | 内存配置 | 说明 |
|-----|---------|------|
| 开发环境 | 4GB | 小规模测试 |
| 测试环境 | 8GB | 中等规模数据 |
| 生产环境 | 16GB+ | 大规模数据 |

---

## 六、日志配置

### 6.1 日志级别

```yaml
log:
  level: info  # debug, info, warn, error
  format: text
  file:
    path: /var/log/milvus
    max_size: 100MB
    max_age: 7d
    max_backups: 3
```

**日志级别说明**：
- **debug**：详细调试信息（开发环境）
- **info**：一般信息（生产环境）
- **warn**：警告信息
- **error**：错误信息

---

### 6.2 查看日志

```bash
# 查看实时日志
docker-compose logs -f standalone

# 查看最近日志
docker-compose logs --tail=100 standalone

# 过滤错误日志
docker-compose logs standalone | grep -i error

# 导出日志
docker-compose logs standalone > milvus.log
```

---

## 七、服务管理

### 7.1 启动/停止服务

```bash
# 启动服务
docker-compose up -d

# 停止服务
docker-compose down

# 重启服务
docker-compose restart

# 查看状态
docker-compose ps
```

### 7.2 升级服务

```bash
# 停止服务
docker-compose down

# 备份数据
cp -r /data/milvus/data /data/milvus/data_backup

# 拉取新版本
docker-compose pull

# 启动新版本
docker-compose up -d

# 验证升级
docker-compose logs -f standalone
```

### 7.3 健康检查

```bash
# 检查 Milvus 健康状态
curl http://localhost:9091/healthz

# 检查 etcd 健康状态
docker exec milvus-etcd etcdctl endpoint health

# 检查 MinIO 健康状态
curl http://localhost:9000/minio/health/live
```

---

## 八、常见部署问题

### 8.1 端口冲突

**现象**：启动时报 "port is already allocated"

**解决方案**：
```bash
# 查找占用端口的进程
lsof -i :19530

# 杀死进程
kill -9 <PID>

# 或者修改端口映射
```

### 8.2 内存不足

**现象**：容器启动后自动退出，日志显示 OOM

**解决方案**：
```bash
# 增加内存限制
# 修改 docker-compose.yml 中的 deploy.resources.limits.memory

# 检查系统内存
free -h

# 启用 Swap（临时解决方案）
fallocate -l 4G /swapfile
chmod 600 /swapfile
mkswap /swapfile
swapon /swapfile
```

### 8.3 网络不通

**现象**：客户端无法连接 Milvus

**解决方案**：
```bash
# 检查端口是否开放
firewall-cmd --list-ports

# 检查安全组配置
# 在阿里云控制台检查安全组规则

# 测试网络连通性
telnet <milvus_host> 19530
```

### 8.4 数据目录权限问题

**现象**：启动时报权限错误

**解决方案**：
```bash
# 设置正确的权限
chown -R 1000:1000 /data/milvus/data

# 或者使用 root 用户运行
docker run -u root ...
```

---

## 九、部署校验清单

- [ ] 硬件资源满足要求
- [ ] Docker 和 Docker Compose 已安装
- [ ] 必要端口已开放（19530, 9091）
- [ ] 数据目录权限正确
- [ ] 资源限制配置合理
- [ ] 服务启动成功
- [ ] 健康检查通过
- [ ] 客户端连接测试成功
- [ ] 日志级别配置正确
- [ ] 备份策略已配置

---

## 附录：部署脚本

```bash
#!/bin/bash

# Milvus 一键部署脚本

# 创建目录结构
mkdir -p /data/milvus/{config,data/etcd,data/minio,data/milvus,logs}

# 创建 docker-compose.yml
cat > /data/milvus/config/docker-compose.yml << 'EOF'
version: "3.8"
services:
  etcd:
    container_name: milvus-etcd
    image: quay.io/coreos/etcd:v3.5.25
    environment:
      - ETCD_AUTO_COMPACTION_MODE=revision
      - ETCD_AUTO_COMPACTION_RETENTION=1000
      - ETCD_QUOTA_BACKEND_BYTES=4294967296
      - ETCD_SNAPSHOT_COUNT=50000
    volumes:
      - ../data/etcd:/etcd
    command: etcd -advertise-client-urls=http://127.0.0.1:2379 -listen-client-urls=http://0.0.0.0:2379 --data-dir /etcd
    restart: unless-stopped

  minio:
    container_name: milvus-minio
    image: minio/minio:RELEASE.2024-12-18T13-15-44Z
    environment:
      MINIO_ACCESS_KEY: minioadmin
      MINIO_SECRET_KEY: minioadmin
    volumes:
      - ../data/minio:/minio_data
    command: minio server /minio_data --console-address ":9001"
    ports:
      - "9000:9000"
      - "9001:9001"
    restart: unless-stopped

  standalone:
    container_name: milvus-standalone
    image: milvusdb/milvus:v2.6.13
    command: ["milvus", "run", "standalone"]
    environment:
      ETCD_ENDPOINTS: etcd:2379
      MINIO_ADDRESS: minio:9000
    volumes:
      - ../data/milvus:/var/lib/milvus
    ports:
      - "19530:19530"
      - "9091:9091"
    depends_on:
      - etcd
      - minio
    restart: unless-stopped
    deploy:
      resources:
        limits:
          memory: 8g
          cpus: '4.0'
EOF

# 启动服务
cd /data/milvus/config
docker-compose up -d

# 等待启动
echo "Waiting for Milvus to start..."
sleep 60

# 验证
echo "Verifying deployment..."
curl -s http://localhost:9091/healthz && echo "Milvus is running!"
```