-- 用户表
CREATE TABLE IF NOT EXISTS users
(
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    username         TEXT   NOT NULL UNIQUE,
    password         TEXT   NOT NULL,
    display_username TEXT,
    avatar_url       TEXT,
    created_at       BIGINT NOT NULL
);

-- 智能体配置表
CREATE TABLE IF NOT EXISTS agent_configs
(
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    name              TEXT   NOT NULL,
    avatar            TEXT,
    system_prompt     TEXT,
    enabled_tools     TEXT,
    enabled_mcp_tools TEXT,
    default_llm_id    BIGINT,
    workspace_folder  TEXT,
    created_at        BIGINT NOT NULL,
    updated_at        BIGINT NOT NULL
);

-- LLM配置表
CREATE TABLE IF NOT EXISTS llm_configs
(
    id                 INTEGER PRIMARY KEY AUTOINCREMENT,
    name               TEXT   NOT NULL,
    api_url            TEXT   NOT NULL,
    api_key            TEXT,
    model              TEXT   NOT NULL,
    max_context_length INTEGER,
    temperature        REAL,
    created_at         BIGINT NOT NULL,
    updated_at         BIGINT NOT NULL
);

-- 会话表
CREATE TABLE IF NOT EXISTS conversations
(
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    agent_config_id BIGINT NOT NULL,
    title           TEXT,
    type            TEXT   DEFAULT 'CHAT',
    created_at      BIGINT NOT NULL,
    updated_at      BIGINT NOT NULL
);

-- 消息记录表（用于向量检索）
CREATE TABLE IF NOT EXISTS messages
(
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    conversation_id BIGINT NOT NULL,
    role            TEXT   NOT NULL,
    content         TEXT   NOT NULL,
    embedding       BLOB,
    api_url         TEXT,
    model           TEXT,
    input_tokens    BIGINT,
    output_tokens   BIGINT,
    created_at      BIGINT NOT NULL
);

-- 待办事项表
CREATE TABLE IF NOT EXISTS todo_items
(
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    conversation_id BIGINT NOT NULL,
    text            TEXT   NOT NULL,
    done            INTEGER DEFAULT 0,
    sort_order      INTEGER DEFAULT 0,
    created_at      BIGINT NOT NULL,
    done_at         BIGINT
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_users_username ON users (username);
CREATE INDEX IF NOT EXISTS idx_agent_configs_name ON agent_configs (name);
CREATE INDEX IF NOT EXISTS idx_conversations_agent_config_id ON conversations (agent_config_id);
CREATE INDEX IF NOT EXISTS idx_messages_conversation_id ON messages (conversation_id);
CREATE INDEX IF NOT EXISTS idx_todo_items_conversation_id ON todo_items (conversation_id);

-- MCP配置表
CREATE TABLE IF NOT EXISTS mcp_configs
(
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    name           TEXT   NOT NULL UNIQUE,
    transport_type TEXT   NOT NULL,
    command        TEXT,
    args           TEXT,
    env_vars       TEXT,
    url            TEXT,
    created_at     BIGINT NOT NULL,
    updated_at     BIGINT NOT NULL
);

-- 定时任务表
CREATE TABLE IF NOT EXISTS scheduled_tasks
(
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    name                TEXT   NOT NULL,
    agent_config_id     BIGINT NOT NULL,
    user_prompt         TEXT   NOT NULL,
    cron_expression     TEXT   NOT NULL,
    new_session_each    INTEGER DEFAULT 0,
    bound_conversation_id BIGINT,
    enabled             INTEGER DEFAULT 1,
    last_executed_at    BIGINT,
    created_at          BIGINT NOT NULL,
    updated_at          BIGINT NOT NULL
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_scheduled_tasks_enabled ON scheduled_tasks (enabled);

-- 创建vec0虚拟表（如果sqlite-vec扩展已加载）
-- 注意：这个表需要动态创建，在代码中检测扩展是否可用后再创建
-- CREATE VIRTUAL TABLE IF NOT EXISTS message_embeddings USING vec0(embedding float[1536]);
