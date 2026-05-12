CREATE DATABASE IF NOT EXISTS super_biz_agent
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE super_biz_agent;

CREATE TABLE IF NOT EXISTS users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '用户主键',
    username VARCHAR(64) NOT NULL COMMENT '登录用户名',
    nickname VARCHAR(64) NOT NULL COMMENT '昵称',
    avatar_url VARCHAR(255) DEFAULT NULL COMMENT '头像地址',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1-正常 0-禁用',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_users_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户主表';

CREATE TABLE IF NOT EXISTS user_auth (
    user_id BIGINT PRIMARY KEY COMMENT '用户ID',
    password_hash VARCHAR(255) NOT NULL COMMENT '密码哈希值',
    password_salt VARCHAR(64) DEFAULT NULL COMMENT '预留盐值字段',
    last_login_at DATETIME DEFAULT NULL COMMENT '最后登录时间',
    login_fail_count INT NOT NULL DEFAULT 0 COMMENT '登录失败次数',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    CONSTRAINT fk_user_auth_user
      FOREIGN KEY (user_id) REFERENCES users(id)
      ON DELETE CASCADE
      ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户认证表';

CREATE TABLE IF NOT EXISTS chat_session_meta (
    session_id VARCHAR(64) PRIMARY KEY COMMENT '会话ID',
    user_id BIGINT NOT NULL COMMENT '会话归属用户ID',
    title VARCHAR(128) DEFAULT NULL COMMENT '会话标题',
    last_message_at DATETIME DEFAULT NULL COMMENT '最后消息时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    CONSTRAINT fk_chat_session_meta_user
      FOREIGN KEY (user_id) REFERENCES users(id)
      ON DELETE CASCADE
      ON UPDATE RESTRICT,
    KEY idx_chat_session_meta_user_updated (user_id, updated_at),
    KEY idx_chat_session_meta_last_message_at (last_message_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='聊天会话元数据表';

CREATE TABLE IF NOT EXISTS chat_message(
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '消息ID（自增主键）',
    session_id VARCHAR(64) NOT NULL COMMENT '会话ID，关联chat_session_meta',
    user_id BIGINT NOT NULL COMMENT '会话归属用户ID',
    role TINYINT NOT NULL COMMENT '消息角色：0-system 1-user 2-assistant',
    content MEDIUMTEXT NOT NULL COMMENT '消息内容',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '消息创建时间',
    CONSTRAINT fk_chat_message_session
        FOREIGN KEY (session_id) REFERENCES chat_session_meta(session_id)
            ON DELETE CASCADE
            ON UPDATE RESTRICT,
    CONSTRAINT fk_chat_message_user
        FOREIGN KEY (user_id) REFERENCES users(id)
            ON DELETE CASCADE
            ON UPDATE RESTRICT,
    -- 索引：按会话查消息，超快
    KEY idx_chat_message_session_created (session_id, created_at),
    KEY idx_chat_message_user_created (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='聊天消息明细表';