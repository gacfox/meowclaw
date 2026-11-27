CREATE TABLE IF NOT EXISTS mc_user
(
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    username      VARCHAR(100) NOT NULL COMMENT '用户名',
    password_hash VARCHAR(255) NOT NULL COMMENT '密码哈希',
    display_name  VARCHAR(100) COMMENT '显示名称',
    avatar_url    VARCHAR(500) COMMENT '头像URL',
    created_at    BIGINT       NOT NULL COMMENT '创建时间戳',
    updated_at    BIGINT       NOT NULL COMMENT '更新时间戳',
    PRIMARY KEY (id)
);
COMMENT ON TABLE mc_user IS '用户表';
CREATE UNIQUE INDEX IF NOT EXISTS idx_mc_user_username ON mc_user (username);

CREATE TABLE IF NOT EXISTS mc_sys_config
(
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    config_key   VARCHAR(100) NOT NULL COMMENT '配置键',
    config_value TEXT         NOT NULL COMMENT '配置值',
    created_at   BIGINT       NOT NULL COMMENT '创建时间戳',
    updated_at   BIGINT       NOT NULL COMMENT '更新时间戳',
    PRIMARY KEY (id)
);
COMMENT ON TABLE mc_sys_config IS '系统配置表';
CREATE UNIQUE INDEX IF NOT EXISTS uk_mc_sys_config_key ON mc_sys_config (config_key);

CREATE TABLE IF NOT EXISTS mc_llm
(
    id                       BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    name                     VARCHAR(255) NOT NULL COMMENT '配置名称',
    endpoint_url             VARCHAR(500) NOT NULL COMMENT 'API端点URL',
    sk                       VARCHAR(255) COMMENT 'API密钥',
    model                    VARCHAR(100) NOT NULL COMMENT '模型名称',
    max_tokens               INT COMMENT '最大输出token数',
    context_length           INT COMMENT '模型上下文长度',
    temperature              INT COMMENT '温度参数(×100存储)',
    capabilities             VARCHAR(255) COMMENT '能力标签: vison 图片多模态, reasoning 思考, tool 工具调用',
    reasoning_effort_options TEXT COMMENT '思考强度可选项JSON数组',
    created_at               BIGINT       NOT NULL COMMENT '创建时间戳',
    updated_at               BIGINT       NOT NULL COMMENT '更新时间戳',
    PRIMARY KEY (id)
);
COMMENT ON TABLE mc_llm IS '大语言模型配置表';

CREATE TABLE IF NOT EXISTS mc_agent
(
    id                       BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    name                     VARCHAR(255) NOT NULL COMMENT '智能体名称',
    avatar_url               VARCHAR(500) COMMENT '智能体头像URL',
    persona                  TEXT COMMENT '智能体人设',
    enabled_tools            VARCHAR(2000) COMMENT '启用的内置工具JSON数组',
    enabled_mcp_tools        VARCHAR(2000) COMMENT '启用的MCP工具JSON数组',
    llm_id                   BIGINT       NOT NULL COMMENT '关联大语言模型配置ID',
    secondary_llm_id         BIGINT       NOT NULL COMMENT '关联辅助大语言模型配置ID',
    embedding_model_id       BIGINT COMMENT '向量嵌入模型配置ID',
    workspace_folder         VARCHAR(500) COMMENT '工作区目录路径',
    aggressive_memory_recall BOOLEAN      NOT NULL DEFAULT FALSE COMMENT '是否激进记忆召回: true 是, false 否',
    allow_as_subagent        BOOLEAN      NOT NULL DEFAULT FALSE COMMENT '是否允许作为专业子智能体: true 是, false 否',
    subagent_description     TEXT COMMENT '子智能体描述提示词, 用于主智能体编排调度',
    created_at               BIGINT       NOT NULL COMMENT '创建时间戳',
    updated_at               BIGINT       NOT NULL COMMENT '更新时间戳',
    PRIMARY KEY (id)
);
COMMENT ON TABLE mc_agent IS '智能体表';

CREATE TABLE IF NOT EXISTS mc_agent_subagent
(
    id              BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    parent_agent_id BIGINT NOT NULL COMMENT '父智能体ID',
    subagent_id     BIGINT NOT NULL COMMENT '专业子智能体ID',
    PRIMARY KEY (id)
);
COMMENT ON TABLE mc_agent_subagent IS '智能体子智能体关联表';
CREATE UNIQUE INDEX IF NOT EXISTS uk_agent_subagent_parent_subagent
    ON mc_agent_subagent (parent_agent_id, subagent_id);
CREATE INDEX IF NOT EXISTS idx_mc_agent_subagent_subagent_id ON mc_agent_subagent (subagent_id);

CREATE TABLE IF NOT EXISTS mc_environment_variable
(
    id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    agent_id   BIGINT COMMENT '关联智能体ID, NULL则全局生效',
    key_name   VARCHAR(255) NOT NULL COMMENT '环境变量名',
    key_value  TEXT         NOT NULL COMMENT '环境变量值',
    created_at BIGINT       NOT NULL COMMENT '创建时间戳',
    updated_at BIGINT       NOT NULL COMMENT '更新时间戳',
    PRIMARY KEY (id)
);
COMMENT ON TABLE mc_environment_variable IS '环境变量表';
CREATE UNIQUE INDEX IF NOT EXISTS uk_mc_environment_variable_agent_key
    ON mc_environment_variable (agent_id, key_name);

CREATE TABLE IF NOT EXISTS mc_conversation
(
    id                     BIGINT  NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    agent_id               BIGINT  NOT NULL COMMENT '关联智能体ID',
    title                  VARCHAR(500) COMMENT '会话标题',
    type                   VARCHAR(50)      DEFAULT 'CHAT' COMMENT '会话类型: CHAT 智能体会话, SCHEDULED 定时任务会话, SUBAGENT 子智能体会话',
    context_json           TEXT COMMENT '会话级上下文状态JSON',
    guardrail_policy_id    BIGINT COMMENT '安全护栏策略ID, 为空使用默认策略',
    parent_conversation_id BIGINT COMMENT '父会话ID, 仅子智能体',
    allowed_tools          TEXT COMMENT '子智能体工具白名单JSON数组, 仅子智能体',
    pinned                 BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否钉选: TRUE 是, FALSE 否',
    project_id             BIGINT COMMENT '关联项目ID',
    created_at             BIGINT  NOT NULL COMMENT '创建时间戳',
    updated_at             BIGINT  NOT NULL COMMENT '更新时间戳',
    PRIMARY KEY (id)
);
COMMENT ON TABLE mc_conversation IS '会话表';
CREATE INDEX IF NOT EXISTS idx_mc_conversation_agent_id ON mc_conversation (agent_id);

CREATE TABLE IF NOT EXISTS mc_chat_event_batch
(
    id                       BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    conversation_id          BIGINT      NOT NULL COMMENT '关联会话ID',
    user_content             TEXT        NOT NULL COMMENT '用户输入内容',
    attachments              TEXT COMMENT '附件JSON数组',
    prompt_context_json      TEXT COMMENT '用户轮次上下文快照JSON',
    type                     VARCHAR(32) NOT NULL DEFAULT 'USER' COMMENT '批次类型: USER 用户任务, CONTEXT_COMPACTION 上下文主动压缩',
    status                   VARCHAR(20) NOT NULL DEFAULT 'RUNNING' COMMENT '批次状态: RUNNING 执行中, COMPLETED 已完成, ERROR 出错',
    error_message            TEXT COMMENT '错误信息',
    input_tokens             BIGINT COMMENT '输入token数, 包含缓存',
    output_tokens            BIGINT COMMENT '输出token数',
    cached_tokens            BIGINT COMMENT '缓存输入token数',
    output_tokens_per_second DOUBLE COMMENT '平均输出速度',
    created_at               BIGINT      NOT NULL COMMENT '创建时间戳',
    completed_at             BIGINT COMMENT '完成时间',
    PRIMARY KEY (id)
);
COMMENT ON TABLE mc_chat_event_batch IS '聊天事件批次表';
CREATE INDEX IF NOT EXISTS idx_mc_chat_event_batch_conversation_id ON mc_chat_event_batch (conversation_id);

CREATE TABLE IF NOT EXISTS mc_context_recap
(
    id              BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    conversation_id BIGINT      NOT NULL COMMENT '关联会话ID',
    from_batch_id   BIGINT      NOT NULL COMMENT '覆盖起始批次ID',
    to_batch_id     BIGINT      NOT NULL COMMENT '覆盖结束批次ID',
    type            VARCHAR(32) NOT NULL COMMENT '摘要类型: ROLLING 单批次, ROLLED_UP 合并摘要, PROACTIVE 主动压缩',
    content         TEXT        NOT NULL COMMENT '摘要内容',
    created_at      BIGINT      NOT NULL COMMENT '创建时间戳',
    PRIMARY KEY (id)
);
COMMENT ON TABLE mc_context_recap IS '上下文摘要表';
CREATE INDEX IF NOT EXISTS idx_mc_context_recap_conversation_id ON mc_context_recap (conversation_id);

CREATE TABLE IF NOT EXISTS mc_chat_event
(
    id             BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    batch_id       BIGINT      NOT NULL COMMENT '关联批次ID',
    event_order    INT         NOT NULL COMMENT '事件顺序, 从0开始',
    type           VARCHAR(20) NOT NULL COMMENT '事件类型: THINKING 思考, CONTENT 模型正文, TOOL_CALL 工具调用, TOOL_RESULT 工具结果, ERROR 错误',
    content        TEXT COMMENT '事件内容',
    tool_name      VARCHAR(200) COMMENT '工具名称',
    tool_call_id   VARCHAR(100) COMMENT '工具调用ID',
    tool_arguments TEXT COMMENT '工具参数JSON',
    created_at     BIGINT      NOT NULL COMMENT '创建时间戳',
    PRIMARY KEY (id)
);
COMMENT ON TABLE mc_chat_event IS '聊天事件表';
CREATE INDEX IF NOT EXISTS idx_mc_chat_event_batch_id ON mc_chat_event (batch_id);

CREATE TABLE IF NOT EXISTS mc_message
(
    id                BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    conversation_id   BIGINT      NOT NULL COMMENT '关联会话ID',
    batch_id          BIGINT COMMENT '关联批次ID',
    role              VARCHAR(20) NOT NULL COMMENT '角色: system, user, assistant, tool',
    content           TEXT        NOT NULL COMMENT '消息内容',
    reasoning_content TEXT COMMENT '推理内容',
    tool_calls_json   TEXT COMMENT '工具调用列表JSON',
    tool_call_id      VARCHAR(100) COMMENT '工具调用ID',
    input_tokens      BIGINT COMMENT '输入token数',
    output_tokens     BIGINT COMMENT '输出token数',
    created_at        BIGINT      NOT NULL COMMENT '创建时间戳',
    PRIMARY KEY (id)
);
COMMENT ON TABLE mc_message IS '消息表';
CREATE INDEX IF NOT EXISTS idx_mc_message_conversation_id ON mc_message (conversation_id);
CREATE INDEX IF NOT EXISTS idx_mc_message_batch_id ON mc_message (batch_id);

CREATE TABLE IF NOT EXISTS mc_scheduled_task
(
    id                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    name               VARCHAR(255) NOT NULL COMMENT '任务名称',
    agent_id           BIGINT       NOT NULL COMMENT '关联智能体ID',
    user_prompt        TEXT         NOT NULL COMMENT '用户提示词',
    cron_expression    VARCHAR(100) NOT NULL COMMENT 'Cron表达式',
    create_new_session BOOLEAN      NOT NULL DEFAULT FALSE COMMENT '是否每次创建新会话: FALSE 绑定同一会话, TRUE 每次新会话',
    enabled            BOOLEAN      NOT NULL DEFAULT TRUE COMMENT '是否启用: TRUE 是, FALSE 否',
    last_status        VARCHAR(20) COMMENT '上次执行状态: RUNNING 执行中, SUCCESS 成功, ERROR 失败',
    last_executed_at   BIGINT COMMENT '上次执行时间',
    created_at         BIGINT       NOT NULL COMMENT '创建时间戳',
    updated_at         BIGINT       NOT NULL COMMENT '更新时间戳',
    PRIMARY KEY (id)
);
COMMENT ON TABLE mc_scheduled_task IS '定时任务表';

CREATE TABLE IF NOT EXISTS mc_scheduled_task_execution
(
    id                BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    scheduled_task_id BIGINT      NOT NULL COMMENT '关联定时任务ID',
    conversation_id   BIGINT      NOT NULL COMMENT '关联会话ID',
    status            VARCHAR(20) NOT NULL COMMENT '执行状态: RUNNING 执行中, SUCCESS 成功, ERROR 失败',
    executed_at       BIGINT      NOT NULL COMMENT '执行时间',
    PRIMARY KEY (id)
);
COMMENT ON TABLE mc_scheduled_task_execution IS '定时任务执行记录表';
CREATE INDEX IF NOT EXISTS idx_scheduled_task_execution_task_id ON mc_scheduled_task_execution (scheduled_task_id);

CREATE TABLE IF NOT EXISTS mc_mcp_service_config
(
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    name            VARCHAR(100) NOT NULL COMMENT '服务名, 作为工具名前缀',
    description     VARCHAR(500) COMMENT '描述',
    protocol        VARCHAR(20)  NOT NULL COMMENT '传输协议: STDIO 本地子进程, STREAMABLE_HTTP 流式HTTP, SSE SSE旧协议',
    config          TEXT         NOT NULL COMMENT '协议配置JSON',
    enabled         BOOLEAN      NOT NULL DEFAULT FALSE COMMENT '是否启用: TRUE 是, FALSE 否',
    status          VARCHAR(20)  NOT NULL DEFAULT 'DISCONNECTED' COMMENT '连接状态: CONNECTED 已连接, DISCONNECTED 未连接, ERROR 连接异常',
    tools_cache     TEXT COMMENT '上次成功连接时发现的工具列表JSON缓存',
    error_message   VARCHAR(1000) COMMENT '最近一次错误信息',
    last_checked_at BIGINT COMMENT '上次状态检查时间戳',
    created_at      BIGINT       NOT NULL COMMENT '创建时间戳',
    updated_at      BIGINT       NOT NULL COMMENT '更新时间戳',
    PRIMARY KEY (id)
);
COMMENT ON TABLE mc_mcp_service_config IS 'MCP服务配置表';
CREATE UNIQUE INDEX IF NOT EXISTS uk_mcp_service_config_name ON mc_mcp_service_config (name);

CREATE TABLE IF NOT EXISTS mc_skill_package
(
    id                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    name              VARCHAR(64)  NOT NULL COMMENT '技能名',
    description       VARCHAR(1000) COMMENT '技能描述',
    stored_filename   VARCHAR(200) NOT NULL COMMENT '落盘文件名',
    original_filename VARCHAR(255) NOT NULL COMMENT '用户上传时的原始文件名',
    file_size         BIGINT       NOT NULL COMMENT '压缩包字节数',
    created_at        BIGINT       NOT NULL COMMENT '创建时间戳',
    updated_at        BIGINT       NOT NULL COMMENT '更新时间戳',
    PRIMARY KEY (id)
);
COMMENT ON TABLE mc_skill_package IS '技能包表';

CREATE TABLE IF NOT EXISTS mc_llm_call_log
(
    id                  BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    llm_id              BIGINT COMMENT '关联LLM配置ID',
    agent_id            BIGINT COMMENT '关联智能体ID',
    conversation_id     BIGINT COMMENT '关联会话ID',
    batch_id            BIGINT COMMENT '关联批次ID',
    model               VARCHAR(100) COMMENT '模型名',
    purpose             VARCHAR(32) COMMENT '调用用途: agent 主循环, title 标题, recap 摘要, memory 记忆',
    request_messages    TEXT COMMENT '请求消息列表JSON',
    response_content    TEXT COMMENT '响应内容',
    response_tool_calls TEXT COMMENT '响应工具调用JSON',
    reasoning_content   TEXT COMMENT '推理内容',
    input_tokens        BIGINT      NOT NULL DEFAULT 0 COMMENT '输入token数',
    output_tokens       BIGINT      NOT NULL DEFAULT 0 COMMENT '输出token数',
    total_tokens        BIGINT      NOT NULL DEFAULT 0 COMMENT '合计token数',
    cached_tokens       BIGINT COMMENT '缓存输入token数',
    duration_ms         BIGINT COMMENT '调用耗时',
    status              VARCHAR(20) NOT NULL COMMENT '调用状态: SUCCESS 成功, ERROR 失败',
    error_message       TEXT COMMENT '错误信息',
    created_at          BIGINT      NOT NULL COMMENT '调用时间',
    PRIMARY KEY (id)
);
COMMENT ON TABLE mc_llm_call_log IS 'LLM调用日志表';
CREATE INDEX IF NOT EXISTS idx_mc_llm_call_log_batch_id ON mc_llm_call_log (batch_id);
CREATE INDEX IF NOT EXISTS idx_mc_llm_call_log_created_at ON mc_llm_call_log (created_at);
CREATE INDEX IF NOT EXISTS idx_mc_llm_call_log_conversation_id ON mc_llm_call_log (conversation_id);

CREATE TABLE IF NOT EXISTS mc_token_usage
(
    id            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    llm_id        BIGINT COMMENT '关联LLM配置ID',
    agent_id      BIGINT COMMENT '关联智能体ID',
    model         VARCHAR(100) COMMENT '模型名',
    purpose       VARCHAR(32) COMMENT '调用用途',
    status        VARCHAR(20) NOT NULL COMMENT '调用状态: SUCCESS 成功, ERROR 失败',
    input_tokens  BIGINT      NOT NULL DEFAULT 0 COMMENT '输入token数',
    output_tokens BIGINT      NOT NULL DEFAULT 0 COMMENT '输出token数',
    total_tokens  BIGINT      NOT NULL DEFAULT 0 COMMENT '合计token数',
    cached_tokens BIGINT COMMENT '缓存输入token数',
    created_at    BIGINT      NOT NULL COMMENT '调用时间',
    PRIMARY KEY (id)
);
COMMENT ON TABLE mc_token_usage IS 'Token用量表';
CREATE INDEX IF NOT EXISTS idx_mc_token_usage_created_at ON mc_token_usage (created_at);
CREATE INDEX IF NOT EXISTS idx_mc_token_usage_llm_created_at ON mc_token_usage (llm_id, created_at);

CREATE TABLE IF NOT EXISTS mc_embedding_model
(
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    name         VARCHAR(255) NOT NULL COMMENT '配置名称',
    endpoint_url VARCHAR(500) NOT NULL COMMENT 'API端点URL',
    sk           VARCHAR(255) COMMENT 'API密钥',
    model        VARCHAR(100) NOT NULL COMMENT '模型名称',
    dimensions   INT          NOT NULL COMMENT '向量维度',
    created_at   BIGINT       NOT NULL COMMENT '创建时间戳',
    updated_at   BIGINT       NOT NULL COMMENT '更新时间戳',
    PRIMARY KEY (id)
);
COMMENT ON TABLE mc_embedding_model IS '向量嵌入模型配置表';

CREATE TABLE IF NOT EXISTS mc_guardrail_policy
(
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    name        VARCHAR(100) NOT NULL COMMENT '策略名',
    config_json TEXT         NOT NULL COMMENT '策略配置JSON',
    builtin     BOOLEAN      NOT NULL DEFAULT FALSE COMMENT '是否内置策略, 内置策略为只读',
    created_at  BIGINT       NOT NULL COMMENT '创建时间戳',
    updated_at  BIGINT       NOT NULL COMMENT '更新时间戳',
    PRIMARY KEY (id)
);
COMMENT ON TABLE mc_guardrail_policy IS '安全护栏策略表';
CREATE UNIQUE INDEX IF NOT EXISTS uk_mc_guardrail_policy_name ON mc_guardrail_policy (name);

CREATE TABLE IF NOT EXISTS mc_project
(
    id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    agent_id   BIGINT       NOT NULL COMMENT '所属智能体ID',
    name       VARCHAR(200) NOT NULL COMMENT '项目名称',
    path       VARCHAR(1000) COMMENT '项目工作目录',
    created_at BIGINT       NOT NULL COMMENT '创建时间戳',
    updated_at BIGINT       NOT NULL COMMENT '更新时间戳',
    PRIMARY KEY (id)
);
COMMENT ON TABLE mc_project IS '项目表';
CREATE UNIQUE INDEX IF NOT EXISTS uk_mc_project_agent_name ON mc_project (agent_id, name);
CREATE UNIQUE INDEX IF NOT EXISTS uk_mc_project_agent_path ON mc_project (agent_id, path);

CREATE TABLE IF NOT EXISTS mc_memory_node
(
    id               BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    agent_id         BIGINT      NOT NULL COMMENT '智能体ID',
    type             VARCHAR(32) NOT NULL COMMENT '类型: fact 事实, preference 偏好, rule 规则',
    content          TEXT        NOT NULL COMMENT '记忆内容',
    last_accessed_at BIGINT COMMENT '最后访问时间戳',
    created_at       BIGINT      NOT NULL COMMENT '创建时间戳',
    updated_at       BIGINT      NOT NULL COMMENT '更新时间戳',
    PRIMARY KEY (id)
);
COMMENT ON TABLE mc_memory_node IS '记忆节点表';
CREATE INDEX IF NOT EXISTS idx_mc_memory_node_agent_id ON mc_memory_node (agent_id);
CREATE INDEX IF NOT EXISTS idx_mc_memory_node_type ON mc_memory_node (type);

CREATE TABLE IF NOT EXISTS mc_memory_entity
(
    id               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    agent_id         BIGINT       NOT NULL COMMENT '智能体ID',
    name             VARCHAR(255) NOT NULL COMMENT '实体名',
    last_accessed_at BIGINT COMMENT '最后访问时间戳',
    created_at       BIGINT       NOT NULL COMMENT '创建时间戳',
    updated_at       BIGINT       NOT NULL COMMENT '更新时间戳',
    PRIMARY KEY (id)
);
COMMENT ON TABLE mc_memory_entity IS '记忆实体表';
CREATE INDEX IF NOT EXISTS idx_mc_memory_entity_agent_id ON mc_memory_entity (agent_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_mc_memory_entity_agent_name ON mc_memory_entity (agent_id, name);

CREATE TABLE IF NOT EXISTS mc_memory_node_entity
(
    id               BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    agent_id         BIGINT NOT NULL COMMENT '智能体ID',
    node_id          BIGINT NOT NULL COMMENT '关联记忆节点ID',
    entity_id        BIGINT NOT NULL COMMENT '关联实体ID',
    description      TEXT   NOT NULL COMMENT '关系描述',
    last_accessed_at BIGINT COMMENT '最后访问时间戳',
    created_at       BIGINT NOT NULL COMMENT '创建时间戳',
    updated_at       BIGINT NOT NULL COMMENT '更新时间戳',
    PRIMARY KEY (id)
);
COMMENT ON TABLE mc_memory_node_entity IS '记忆节点实体关联表';
CREATE INDEX IF NOT EXISTS idx_mc_memory_node_entity_agent_id ON mc_memory_node_entity (agent_id);
CREATE INDEX IF NOT EXISTS idx_mc_memory_node_entity_node_id ON mc_memory_node_entity (node_id);
CREATE INDEX IF NOT EXISTS idx_mc_memory_node_entity_entity_id ON mc_memory_node_entity (entity_id);
