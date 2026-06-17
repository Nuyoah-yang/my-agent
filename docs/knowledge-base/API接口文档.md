# Super Biz Agent API 接口文档

## 一、接口概述

### 1.1 基本信息

| 项目 | 描述 |
|-----|------|
| **服务名称** | Super Biz Agent |
| **API 版本** | v1 |
| **基础路径** | `/api/v1` |
| **协议** | HTTP/HTTPS |
| **认证方式** | JWT Token |

### 1.2 错误响应格式

```json
{
  "code": 400,
  "message": "错误描述",
  "timestamp": "2024-01-15T10:30:00Z",
  "path": "/api/v1/chat"
}
```

---

## 二、认证接口

### 2.1 用户登录

**POST** `/api/v1/auth/login`

**请求体**：
```json
{
  "username": "string",
  "password": "string"
}
```

**响应成功**（200 OK）：
```json
{
  "code": 200,
  "message": "登录成功",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "tokenType": "Bearer",
    "expiresIn": 7200,
    "user": {
      "id": 1,
      "username": "admin",
      "role": "admin",
      "email": "admin@example.com"
    }
  }
}
```

**响应失败**（401 Unauthorized）：
```json
{
  "code": 401,
  "message": "用户名或密码错误"
}
```

### 2.2 刷新 Token

**POST** `/api/v1/auth/refresh`

**请求头**：
```
Authorization: Bearer <refresh_token>
```

**响应成功**（200 OK）：
```json
{
  "code": 200,
  "message": "刷新成功",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "expiresIn": 7200
  }
}
```

---

## 三、聊天接口

### 3.1 发送消息

**POST** `/api/v1/chat`

**请求头**：
```
Authorization: Bearer <access_token>
Content-Type: application/json
```

**请求体**：
```json
{
  "sessionId": "string (可选，不传则创建新会话)",
  "message": "string (必填，用户消息内容)",
  "useRag": true (可选，是否使用知识库检索，默认true)
}
```

**响应成功**（200 OK）：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "sessionId": "abc123",
    "messageId": "msg-001",
    "answer": "这是AI助手的回答内容...",
    "sources": [
      {
        "title": "文档标题",
        "path": "/documents/xxx.pdf",
        "relevance": 0.85,
        "content": "相关文档片段..."
      }
    ],
    "timestamp": "2024-01-15T10:30:00Z"
  }
}
```

### 3.2 获取会话列表

**GET** `/api/v1/chat/sessions`

**请求头**：
```
Authorization: Bearer <access_token>
```

**请求参数**：
| 参数 | 类型 | 必填 | 说明 |
|-----|------|-----|------|
| page | int | 否 | 页码，默认1 |
| size | int | 否 | 每页数量，默认10 |

**响应成功**（200 OK）：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "content": [
      {
        "sessionId": "abc123",
        "lastMessage": "用户的最后一条消息",
        "lastAnswer": "AI的最后回复",
        "createdAt": "2024-01-15T10:00:00Z",
        "updatedAt": "2024-01-15T10:30:00Z"
      }
    ],
    "totalElements": 10,
    "totalPages": 1,
    "currentPage": 1
  }
}
```

### 3.3 获取会话详情

**GET** `/api/v1/chat/sessions/{sessionId}`

**请求头**：
```
Authorization: Bearer <access_token>
```

**路径参数**：
| 参数 | 类型 | 说明 |
|-----|------|------|
| sessionId | string | 会话ID |

**响应成功**（200 OK）：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "sessionId": "abc123",
    "messages": [
      {
        "messageId": "msg-001",
        "role": "user",
        "content": "用户的问题",
        "timestamp": "2024-01-15T10:20:00Z"
      },
      {
        "messageId": "msg-002",
        "role": "assistant",
        "content": "AI的回答",
        "timestamp": "2024-01-15T10:20:05Z",
        "sources": [...]
      }
    ]
  }
}
```

### 3.4 删除会话

**DELETE** `/api/v1/chat/sessions/{sessionId}`

**请求头**：
```
Authorization: Bearer <access_token>
```

**路径参数**：
| 参数 | 类型 | 说明 |
|-----|------|------|
| sessionId | string | 会话ID |

**响应成功**（200 OK）：
```json
{
  "code": 200,
  "message": "会话已删除"
}
```

---

## 四、文档接口

### 4.1 上传文档

**POST** `/api/v1/documents/upload`

**请求头**：
```
Authorization: Bearer <access_token>
Content-Type: multipart/form-data
```

**请求参数**：
| 参数 | 类型 | 必填 | 说明 |
|-----|------|-----|------|
| file | File | 是 | 文档文件（支持 txt, md, pdf） |
| title | string | 否 | 文档标题（默认使用文件名） |

**响应成功**（200 OK）：
```json
{
  "code": 200,
  "message": "上传成功",
  "data": {
    "documentId": "doc-001",
    "title": "文档标题",
    "fileName": "example.pdf",
    "fileSize": 102400,
    "chunkCount": 15,
    "status": "processed",
    "createdAt": "2024-01-15T10:30:00Z"
  }
}
```

### 4.2 获取文档列表

**GET** `/api/v1/documents`

**请求头**：
```
Authorization: Bearer <access_token>
```

**请求参数**：
| 参数 | 类型 | 必填 | 说明 |
|-----|------|-----|------|
| page | int | 否 | 页码，默认1 |
| size | int | 否 | 每页数量，默认10 |
| status | string | 否 | 状态筛选（processed/pending/failed） |

**响应成功**（200 OK）：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "content": [
      {
        "documentId": "doc-001",
        "title": "文档标题",
        "fileName": "example.pdf",
        "fileSize": 102400,
        "chunkCount": 15,
        "status": "processed",
        "createdAt": "2024-01-15T10:30:00Z",
        "updatedAt": "2024-01-15T10:30:05Z"
      }
    ],
    "totalElements": 50,
    "totalPages": 5,
    "currentPage": 1
  }
}
```

### 4.3 获取文档详情

**GET** `/api/v1/documents/{documentId}`

**请求头**：
```
Authorization: Bearer <access_token>
```

**路径参数**：
| 参数 | 类型 | 说明 |
|-----|------|------|
| documentId | string | 文档ID |

**响应成功**（200 OK）：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "documentId": "doc-001",
    "title": "文档标题",
    "fileName": "example.pdf",
    "fileSize": 102400,
    "chunkCount": 15,
    "status": "processed",
    "metadata": {
      "author": "张三",
      "category": "技术文档",
      "tags": ["Java", "Spring"]
    },
    "createdAt": "2024-01-15T10:30:00Z",
    "updatedAt": "2024-01-15T10:30:05Z"
  }
}
```

### 4.4 删除文档

**DELETE** `/api/v1/documents/{documentId}`

**请求头**：
```
Authorization: Bearer <access_token>
```

**路径参数**：
| 参数 | 类型 | 说明 |
|-----|------|------|
| documentId | string | 文档ID |

**响应成功**（200 OK）：
```json
{
  "code": 200,
  "message": "文档已删除"
}
```

---

## 五、监控接口

### 5.1 获取系统状态

**GET** `/api/v1/monitor/health`

**请求头**：
```
Authorization: Bearer <access_token> (管理员/运维角色)
```

**响应成功**（200 OK）：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "status": "healthy",
    "timestamp": "2024-01-15T10:30:00Z",
    "services": {
      "milvus": {
        "status": "connected",
        "latency": 15
      },
      "redis": {
        "status": "connected",
        "latency": 2
      },
      "database": {
        "status": "connected",
        "latency": 5
      }
    },
    "metrics": {
      "cpuUsage": 45,
      "memoryUsage": 62,
      "diskUsage": 35
    }
  }
}
```

### 5.2 获取监控指标

**GET** `/api/v1/monitor/metrics`

**请求头**：
```
Authorization: Bearer <access_token> (管理员/运维角色)
```

**请求参数**：
| 参数 | 类型 | 必填 | 说明 |
|-----|------|-----|------|
| type | string | 否 | 指标类型（qps/latency/memory/all），默认all |
| duration | string | 否 | 时间范围（1h/6h/24h），默认1h |

**响应成功**（200 OK）：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "qps": {
      "chat": 150,
      "documentUpload": 20,
      "search": 80
    },
    "latency": {
      "chat": {
        "p50": 1200,
        "p90": 2500,
        "p95": 3500
      },
      "search": {
        "p50": 800,
        "p90": 1500,
        "p95": 2000
      }
    },
    "memory": {
      "used": 1228,
      "total": 2048,
      "percentage": 60
    }
  }
}
```

### 5.3 获取告警列表

**GET** `/api/v1/monitor/alerts`

**请求头**：
```
Authorization: Bearer <access_token> (管理员/运维角色)
```

**请求参数**：
| 参数 | 类型 | 必填 | 说明 |
|-----|------|-----|------|
| status | string | 否 | 告警状态（active/resolved/all），默认all |
| severity | string | 否 | 严重级别（critical/warning/info） |

**响应成功**（200 OK）：
```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "alertId": "alert-001",
      "name": "MilvusHighMemoryUsage",
      "severity": "warning",
      "status": "active",
      "message": "Milvus内存使用率超过85%",
      "value": 87,
      "createdAt": "2024-01-15T10:20:00Z",
      "resolvedAt": null
    }
  ]
}
```

---

## 六、用户管理接口

### 6.1 获取用户列表

**GET** `/api/v1/users`

**请求头**：
```
Authorization: Bearer <access_token> (管理员角色)
```

**请求参数**：
| 参数 | 类型 | 必填 | 说明 |
|-----|------|-----|------|
| page | int | 否 | 页码，默认1 |
| size | int | 否 | 每页数量，默认10 |
| role | string | 否 | 角色筛选 |

**响应成功**（200 OK）：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "content": [
      {
        "id": 1,
        "username": "admin",
        "email": "admin@example.com",
        "role": "admin",
        "status": "active",
        "createdAt": "2024-01-01T00:00:00Z"
      }
    ],
    "totalElements": 50,
    "totalPages": 5,
    "currentPage": 1
  }
}
```

### 6.2 创建用户

**POST** `/api/v1/users`

**请求头**：
```
Authorization: Bearer <access_token> (管理员角色)
Content-Type: application/json
```

**请求体**：
```json
{
  "username": "string (必填，用户名)",
  "password": "string (必填，密码)",
  "email": "string (必填，邮箱)",
  "role": "string (必填，角色：user/admin/operator)"
}
```

**响应成功**（201 Created）：
```json
{
  "code": 201,
  "message": "用户创建成功",
  "data": {
    "id": 2,
    "username": "newuser",
    "email": "newuser@example.com",
    "role": "user",
    "status": "active",
    "createdAt": "2024-01-15T10:30:00Z"
  }
}
```

### 6.3 更新用户

**PUT** `/api/v1/users/{userId}`

**请求头**：
```
Authorization: Bearer <access_token> (管理员角色)
Content-Type: application/json
```

**路径参数**：
| 参数 | 类型 | 说明 |
|-----|------|------|
| userId | int | 用户ID |

**请求体**：
```json
{
  "email": "string (可选，邮箱)",
  "role": "string (可选，角色)",
  "status": "string (可选，状态：active/inactive)"
}
```

**响应成功**（200 OK）：
```json
{
  "code": 200,
  "message": "用户更新成功",
  "data": {
    "id": 2,
    "username": "newuser",
    "email": "updated@example.com",
    "role": "user",
    "status": "active"
  }
}
```

### 6.4 删除用户

**DELETE** `/api/v1/users/{userId}`

**请求头**：
```
Authorization: Bearer <access_token> (管理员角色)
```

**路径参数**：
| 参数 | 类型 | 说明 |
|-----|------|------|
| userId | int | 用户ID |

**响应成功**（200 OK）：
```json
{
  "code": 200,
  "message": "用户已删除"
}
```

---

## 七、错误码列表

| 错误码 | 含义 | HTTP状态码 |
|-------|------|-----------|
| 400 | 请求参数错误 | 400 |
| 401 | 未授权或Token无效 | 401 |
| 403 | 权限不足 | 403 |
| 404 | 资源不存在 | 404 |
| 500 | 服务器内部错误 | 500 |
| 1001 | 用户不存在 | 404 |
| 1002 | 密码错误 | 401 |
| 1003 | 会话不存在 | 404 |
| 1004 | 文档不存在 | 404 |
| 1005 | 文件格式不支持 | 400 |
| 2001 | Milvus连接失败 | 503 |
| 2002 | Redis连接失败 | 503 |
| 2003 | 数据库连接失败 | 503 |

---

## 八、使用示例

### 8.1 cURL 示例

**登录**：
```bash
curl -X POST http://localhost:9900/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "admin", "password": "password"}'
```

**发送消息**：
```bash
curl -X POST http://localhost:9900/api/v1/chat \
  -H "Authorization: Bearer <access_token>" \
  -H "Content-Type: application/json" \
  -d '{"message": "公司请假流程是怎样的？"}'
```

**上传文档**：
```bash
curl -X POST http://localhost:9900/api/v1/documents/upload \
  -H "Authorization: Bearer <access_token>" \
  -F "file=@document.pdf" \
  -F "title=文档标题"
```

### 8.2 Python 示例

```python
import requests

# 登录
response = requests.post(
    "http://localhost:9900/api/v1/auth/login",
    json={"username": "admin", "password": "password"}
)
token = response.json()["data"]["accessToken"]

# 发送消息
response = requests.post(
    "http://localhost:9900/api/v1/chat",
    headers={"Authorization": f"Bearer {token}"},
    json={"message": "公司请假流程是怎样的？"}
)
print(response.json())
```