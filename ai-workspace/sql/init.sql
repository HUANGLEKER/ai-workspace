-- AI Workspace v1.0 数据库初始化脚本
-- MySQL 8.x

CREATE DATABASE IF NOT EXISTS ai_workspace DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE ai_workspace;

-- =====================================================
-- 1. 用户体系
-- =====================================================

CREATE TABLE IF NOT EXISTS sys_user (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    username    VARCHAR(50)  NOT NULL UNIQUE COMMENT '用户名',
    password    VARCHAR(255) NOT NULL COMMENT '密码（BCrypt）',
    nickname    VARCHAR(50)  COMMENT '昵称',
    avatar      VARCHAR(500) COMMENT '头像URL',
    email       VARCHAR(100) COMMENT '邮箱',
    phone       VARCHAR(20)  COMMENT '手机号',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态 1正常 0禁用',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    create_time DATETIME     COMMENT '创建时间',
    update_time DATETIME     COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户表';

CREATE TABLE IF NOT EXISTS sys_role (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    role_name   VARCHAR(50)  NOT NULL COMMENT '角色名称',
    role_code   VARCHAR(50)  NOT NULL UNIQUE COMMENT '角色标识',
    remark      VARCHAR(255) COMMENT '备注',
    deleted     TINYINT      NOT NULL DEFAULT 0,
    create_time DATETIME     COMMENT '创建时间',
    update_time DATETIME     COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

CREATE TABLE IF NOT EXISTS sys_menu (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    parent_id   BIGINT       NOT NULL DEFAULT 0 COMMENT '父菜单ID',
    menu_name   VARCHAR(50)  NOT NULL COMMENT '菜单名称',
    path        VARCHAR(200) COMMENT '路由路径',
    component   VARCHAR(200) COMMENT '组件路径',
    permission  VARCHAR(100) COMMENT '权限标识',
    menu_type   CHAR(1)      NOT NULL COMMENT 'M目录 C菜单 F按钮',
    sort        INT          NOT NULL DEFAULT 0 COMMENT '排序',
    icon        VARCHAR(100) COMMENT '图标',
    visible     TINYINT      NOT NULL DEFAULT 1 COMMENT '是否显示',
    deleted     TINYINT      NOT NULL DEFAULT 0,
    create_time DATETIME     COMMENT '创建时间',
    update_time DATETIME     COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='菜单表';

CREATE TABLE IF NOT EXISTS sys_user_role (
    user_id BIGINT NOT NULL COMMENT '用户ID',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    PRIMARY KEY (user_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联表';

CREATE TABLE IF NOT EXISTS sys_role_menu (
    role_id BIGINT NOT NULL COMMENT '角色ID',
    menu_id BIGINT NOT NULL COMMENT '菜单ID',
    PRIMARY KEY (role_id, menu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色菜单关联表';

-- =====================================================
-- 2. Chat 模块
-- =====================================================

CREATE TABLE IF NOT EXISTS chat_session (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL COMMENT '用户ID',
    title       VARCHAR(255) NOT NULL DEFAULT '新对话' COMMENT '会话标题',
    model_name  VARCHAR(100) COMMENT '使用的模型',
    deleted     TINYINT      NOT NULL DEFAULT 0,
    create_time DATETIME     COMMENT '创建时间',
    update_time DATETIME     COMMENT '更新时间',
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='聊天会话表';

CREATE TABLE IF NOT EXISTS chat_message (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id  BIGINT       NOT NULL COMMENT '会话ID',
    role        VARCHAR(20)  NOT NULL COMMENT 'user/assistant/system',
    content     LONGTEXT     NOT NULL COMMENT '消息内容',
    token_count INT          COMMENT '消耗Token数',
    deleted     TINYINT      NOT NULL DEFAULT 0,
    create_time DATETIME     COMMENT '创建时间',
    update_time DATETIME     COMMENT '更新时间',
    INDEX idx_session_id (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='聊天消息表';

CREATE TABLE IF NOT EXISTS chat_model (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    model_name  VARCHAR(100) NOT NULL COMMENT '模型名称',
    provider    VARCHAR(50)  NOT NULL COMMENT '提供商（OpenAI/Ollama等）',
    api_url     VARCHAR(500) COMMENT 'API地址',
    api_key     VARCHAR(500) COMMENT 'API Key（加密存储）',
    enabled     TINYINT      NOT NULL DEFAULT 1 COMMENT '是否启用',
    deleted     TINYINT      NOT NULL DEFAULT 0,
    create_time DATETIME     COMMENT '创建时间',
    update_time DATETIME     COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模型配置表';

-- =====================================================
-- 3. 知识库模块
-- =====================================================

CREATE TABLE IF NOT EXISTS kb_knowledge_base (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    kb_name     VARCHAR(100) NOT NULL COMMENT '知识库名称',
    description TEXT         COMMENT '描述',
    create_by   BIGINT       COMMENT '创建人ID',
    deleted     TINYINT      NOT NULL DEFAULT 0,
    create_time DATETIME     COMMENT '创建时间',
    update_time DATETIME     COMMENT '更新时间',
    INDEX idx_create_by (create_by)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库表';

CREATE TABLE IF NOT EXISTS kb_document (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    kb_id       BIGINT       NOT NULL COMMENT '知识库ID',
    file_name   VARCHAR(255) NOT NULL COMMENT '文件名',
    file_path   VARCHAR(500) NOT NULL COMMENT '文件存储路径（MinIO）',
    file_size   BIGINT       COMMENT '文件大小（字节）',
    file_type   VARCHAR(50)  COMMENT '文件类型',
    status      VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PROCESSING/DONE/FAILED',
    deleted     TINYINT      NOT NULL DEFAULT 0,
    create_time DATETIME     COMMENT '创建时间',
    update_time DATETIME     COMMENT '更新时间',
    INDEX idx_kb_id (kb_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库文档表';

CREATE TABLE IF NOT EXISTS kb_chunk_task (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    document_id BIGINT      NOT NULL COMMENT '文档ID',
    task_status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/SUCCESS/FAILED',
    error_msg   TEXT        COMMENT '失败原因',
    deleted     TINYINT     NOT NULL DEFAULT 0,
    create_time DATETIME    COMMENT '创建时间',
    update_time DATETIME    COMMENT '更新时间',
    INDEX idx_document_id (document_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档切片任务表';

-- =====================================================
-- 4. 文件中心
-- =====================================================

CREATE TABLE IF NOT EXISTS file_info (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    file_name   VARCHAR(255) NOT NULL COMMENT '原始文件名',
    file_path   VARCHAR(500) NOT NULL COMMENT 'MinIO存储路径',
    file_size   BIGINT       COMMENT '文件大小（字节）',
    file_type   VARCHAR(50)  COMMENT '文件类型/MIME',
    upload_by   BIGINT       COMMENT '上传人ID',
    deleted     TINYINT      NOT NULL DEFAULT 0,
    create_time DATETIME     COMMENT '创建时间',
    update_time DATETIME     COMMENT '更新时间',
    INDEX idx_upload_by (upload_by)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件信息表';

-- =====================================================
-- 初始数据
-- =====================================================

-- 默认管理员（密码：admin123，BCrypt加密）
INSERT INTO sys_user (username, password, nickname, status, deleted, create_time, update_time)
VALUES ('admin', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '管理员', 1, 0, NOW(), NOW());

-- 管理员角色
INSERT INTO sys_role (role_name, role_code, remark, deleted, create_time, update_time)
VALUES ('超级管理员', 'ROLE_ADMIN', '系统管理员', 0, NOW(), NOW());

-- 绑定用户角色
INSERT INTO sys_user_role (user_id, role_id) VALUES (1, 1);
