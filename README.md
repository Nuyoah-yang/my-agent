# SuperBizAgent

## 项目简介

`SuperBizAgent` 是一个基于 Spring Boot + Spring AI 的智能问答项目，当前已完成聊天接口、会话记忆、用户鉴权、文件上传与向量知识库全链路。

## 当前实现状态

### 基础能力
- [x] 后端基础启动与配置
- [x] 普通聊天接口 `POST /api/chat`
- [x] 流式聊天 `POST /api/chat_stream`（SSE）
- [x] `sessionId` 会话机制（缺省自动生成并回传）
- [x] Redis 会话记忆（多轮对话）
- [x] 会话窗口裁剪
- [x] 用户模块（注册/登录/JWT）

### 文件上传
- [x] 文件上传 `POST /api/upload`（txt/md）
- [x] 上传后自动触发向量索引（分片 → 向量化 → 写入 Milvus）

### 向量管道
- [x] Milvus Collection + Index 自动初始化
- [x] DashScope Embedding 向量化（text-embedding-v4，1024 维）
- [x] 文档智能分片（Markdown 标题 → 段落 → 定长截断 + 重叠）
- [x] 向量索引编排（文件 → 分片 → 向量化 → 入库）
- [x] 向量检索（查询 → 向量化 → Milvus L2 相似度搜索）

### 待完成
- [ ] Agent 工具集成（InternalDocsTools、DateTimeTools 等）
- [ ] 知识库检索与对话上下文融合
- [ ] AIOps 多智能体编排

---

## 向量管道架构

```
文件上传 (/api/upload)
  │
  ├─ FileUploadServiceImpl   → 校验 + 落盘
  │
  └─ VectorIndexService      → 编排入口
       │
       ├─ DocumentChunkService   → 分片（标题/段落/定长+重叠）
       ├─ VectorEmbeddingService → 文本 → 1024维向量（DashScope）
       └─ Milvus Insert          → 写入 biz collection

用户提问
  │
  └─ VectorSearchService     → 检索流程
       ├─ VectorEmbeddingService → 查询向量化
       └─ Milvus Search          → L2 相似度 Top-K
```

### Milvus Collection 结构（biz）

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | VarChar(256) | 主键（UUID，基于 _source + chunkIndex） |
| `vector` | FloatVector(1024) | 文本 Embedding 向量，IVF_FLAT + L2 索引 |
| `content` | VarChar(8192) | 分片文本内容 |
| `metadata` | JSON | 来源文件（_source, _file_name, _extension）+ 分片位置（chunkIndex, totalChunks, title） |

### 分片策略

三层递进：**Markdown 标题切分 → 段落切分（双换行） → 定长截断（max-size=800，overlap=100 重叠）**

---

## 关键配置

`src/main/resources/application.yml`：

- MySQL 连接：`spring.datasource.*`
- Redis 连接：`spring.data.redis.*`
- 模型配置：`spring.ai.dashscope.*`
- 文件上传：`file.upload.path`（绝对路径）、`file.upload.allowed-extensions`
- Milvus：`milvus.*`（`enabled: true` 时启动向量管道）
- Embedding：`dashscope.api.key`、`dashscope.embedding.model`
- 文档分片：`document.chunk.max-size`、`document.chunk.overlap`
- 记忆窗口：`chat.memory.max-messages`
- 记忆过期：`chat.memory.ttl-minutes`
- JWT 配置：`jwt.*`
- MyBatis 配置：`mybatis.*`

---

## 项目结构

```
src/main/java/com/example/super_biz_agent/
├── config/
│   ├── DocumentChunkConfig.java      # 分片配置（document.chunk.*）
│   ├── FileUploadConfig.java         # 上传配置（file.upload.*）
│   ├── MilvusConfig.java             # Milvus 客户端 Bean
│   ├── MilvusConstants.java          # Collection/向量维度等常量
│   ├── MilvusInitializer.java        # 启动时自动建 Collection + Index
│   └── SecurityConfig.java           # Spring Security + JWT
├── controller/
│   ├── AuthController.java           # 注册/登录/当前用户
│   ├── ChatController.java           # 聊天 + 会话管理
│   ├── FileUploadController.java     # 文件上传
│   └── MilvusController.java         # Milvus 健康检查 /milvus/health
├── dto/
│   ├── ApiResponse.java              # 统一响应格式
│   ├── DocumentChunk.java            # 分片 DTO
│   ├── FileUploadRes.java            # 上传结果
│   ├── IndexingResult.java           # 索引结果
│   ├── MilvusHealthData.java         # 健康检查数据
│   └── SearchResult.java             # 向量检索结果
├── service/
│   ├── DocumentChunkService.java     # 分片接口
│   ├── FileUploadService.java        # 上传接口
│   ├── VectorEmbeddingService.java   # Embedding 接口
│   ├── VectorIndexService.java       # 索引编排接口
│   ├── VectorSearchService.java      # 向量检索接口
│   └── serviceImpl/
│       ├── ChatServiceImpl.java
│       ├── DocumentChunkServiceImpl.java
│       ├── FileUploadServiceImpl.java
│       ├── MilvusServiceImpl.java
│       ├── VectorEmbeddingServiceImpl.java
│       ├── VectorIndexServiceImpl.java
│       └── VectorSearchServiceImpl.java
└── ...
```

---

## 用户模块（MyBatis + JWT）

### 核心表

- `users`：用户主表（账号、昵称、状态）
- `user_auth`：认证信息（密码哈希、登录状态）
- `chat_session_meta`：会话归属（`session_id -> user_id`）
- `chat_message`：聊天消息持久化

建表脚本见：`src/main/resources/db/schema_v1_user_module.sql`

### 接口

| 接口 | 说明 |
|------|------|
| `POST /api/auth/register` | 用户注册 |
| `POST /api/auth/login` | 用户登录，返回 JWT |
| `GET /api/auth/me` | 获取当前用户信息 |
| `POST /api/upload` | 文件上传（需登录） |
| `GET /milvus/health` | Milvus 健康检查（免登录） |

### 聊天鉴权规则

- 所有聊天接口需携带 `Authorization: Bearer <token>`
- 新会话：自动生成 `sessionId` 并写入 `chat_session_meta`
- 老会话：校验 `sessionId` 是否归属当前 `userId`
- 不归属则返回错误（越权拦截）

---

## 统一错误码约定

- `200`：请求成功
- `400`：请求参数不合法
- `401`：未登录或无权访问（JWT 过期、会话越权）
- `500`：服务端内部错误

控制层/服务层抛出的异常由 `GlobalExceptionHandler` 统一转换为 `ApiResponse`。

---

## 本地联调建议

1. 启动 MySQL + Redis（确保 `application.yml` 中连接信息可连通）
2. 启动 Milvus：`docker compose -f vector-database.yml up -d`
3. 配置环境变量 `DASHSCOPE_API_KEY`
4. `application.yml` 中设置 `milvus.enabled: true`，`file.upload.path` 改为绝对路径
5. 启动后端服务
6. 完成用户注册/登录并获取 JWT
7. 上传知识库文件（txt/md），观察日志确认分片→向量→入库成功
8. 使用 JWT 调用 chat 接口，验证多轮记忆与越权拦截
