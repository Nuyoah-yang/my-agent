# SuperBizAgent

## 项目简介

`SuperBizAgent` 是一个基于 Spring Boot + Spring AI 的智能问答项目，当前重点完成了聊天接口与会话记忆能力，并预留了 Milvus 与 AIOps 扩展能力。

## 当前实现状态

- [x] 后端基础启动与配置
- [x] 普通聊天接口 `POST /api/chat`
- [x] `sessionId` 会话机制（缺省自动生成并回传）
- [x] Redis 会话记忆（多轮对话）
- [x] 会话窗口裁剪（当前保留 5 轮，即 10 条消息）
- [x] 用户模块（注册/登录/JWT）
- [x] 会话归属校验（session 绑定 user）
- [ ] 流式聊天 `POST /api/chat_stream`（后续完善）
- [ ] 向量检索与知识库联动（后续完善）

## Chat 记忆机制（当前版本）

### 会话规则

- 聊天请求体：`{ "sessionId"?: string, "message": string }`
- 首次不传 `sessionId` 时，后端自动生成 UUID
- 响应体会回传 `sessionId`，前端需保存并复用

### Redis 存储规则

- Key 格式：`chat:session:{sessionId}`
- Value：消息数组（含 `messageType` 与 `text`）
- TTL：30 分钟（可通过配置调整）
- 每轮对话显式写入两条消息：`USER` + `ASSISTANT`

### 窗口策略

- 当前配置为最多 10 条消息（等价 5 轮）
- 超出窗口后由 `MessageWindowChatMemory` 自动裁剪旧消息

## 关键配置

`src/main/resources/application.yml`：

- MySQL 连接：`spring.datasource.*`
- Redis 连接：`spring.data.redis.*`
- 模型配置：`spring.ai.dashscope.*`
- 记忆窗口：`chat.memory.max-messages`
- 记忆过期：`chat.memory.ttl-minutes`
- JWT 配置：`jwt.*`
- MyBatis 配置：`mybatis.*`

## 用户模块（MyBatis + JWT）

### 核心表

- `users`：用户主表（账号、昵称、状态）
- `user_auth`：认证信息（密码哈希、登录状态）
- `chat_session_meta`：会话归属（`session_id -> user_id`）

建表脚本见：`src/main/resources/db/schema_v1_user_module.sql`

### 新增接口

- `POST /api/auth/register`
- `POST /api/auth/login`
- `GET /api/auth/me`

### 聊天鉴权规则

- 所有聊天接口需携带 `Authorization: Bearer <token>`
- 新会话：自动生成 `sessionId` 并写入 `chat_session_meta`
- 老会话：校验 `sessionId` 是否归属当前 `userId`
- 不归属则返回错误（越权拦截）

## 统一错误码约定

- `200`：请求成功
- `400`：请求参数不合法（例如 `sessionId` 为空）
- `401`：未登录或无权访问（例如 JWT 过期、会话越权）
- `500`：服务端内部错误

说明：

- 控制层/服务层抛出的异常由全局异常处理器统一转换为 `ApiResponse`。
- JWT 过滤器在令牌过期或非法时会直接返回 HTTP `401`，前端可据此清理 token 并引导重新登录。

## 本地联调建议

1. 启动 Redis（确保 `application.yml` 中 host/port/password 可连通）
2. 配置环境变量 `DASHSCOPE_API_KEY`
3. 启动后端服务
4. 完成用户注册/登录并获取 JWT
5. 使用 JWT 调用 chat 接口，验证多轮记忆与越权拦截

示例验证：

- 第 1 轮：`我叫小王，请记住`
- 第 2 轮：`我叫什么名字？`

越权验证：

- 用户A登录后创建会话得到 `sessionId=A1`
- 用户B登录后携带自己的 JWT 访问 `A1`
- 预期：后端返回无权访问错误

## 后续规划

- 补齐流式接口与前端 sessionId 同步
- 打通知识库检索与对话上下文融合
- 扩展用户体系（角色、权限、租户）
