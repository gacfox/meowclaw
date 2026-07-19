CREATE TABLE IF NOT EXISTS mc_user (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    username VARCHAR(100) NOT NULL COMMENT '用户名',
    password_hash VARCHAR(255) NOT NULL COMMENT '密码哈希(bcrypt)',
    display_name VARCHAR(100) COMMENT '显示名称',
    avatar_url VARCHAR(500) COMMENT '头像URL',
    created_at BIGINT NOT NULL COMMENT '创建时间(时间戳毫秒)',
    updated_at BIGINT NOT NULL COMMENT '更新时间(时间戳毫秒)',
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_mc_user_username ON mc_user(username);

CREATE TABLE IF NOT EXISTS mc_llm (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    name VARCHAR(255) NOT NULL COMMENT '配置名称',
    endpoint_url VARCHAR(500) NOT NULL COMMENT 'API端点URL',
    sk VARCHAR(255) COMMENT 'API密钥',
    model VARCHAR(100) NOT NULL COMMENT '模型名称',
    max_tokens INT COMMENT '最大token数',
    context_length INT COMMENT '模型上下文长度',
    temperature INT COMMENT '温度参数(×100存储)',
    capabilities VARCHAR(255) COMMENT '能力标签',
    created_at BIGINT NOT NULL COMMENT '创建时间',
    updated_at BIGINT NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS mc_agent (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    name VARCHAR(255) NOT NULL COMMENT '智能体名称',
    avatar_url VARCHAR(500) COMMENT '头像URL',
    persona TEXT COMMENT '人设',
    enabled_tools VARCHAR(2000) COMMENT '启用的内置工具名称(JSON数组)',
    enabled_mcp_tools VARCHAR(2000) COMMENT '启用的MCP工具(JSON数组)',
    llm_id BIGINT NOT NULL COMMENT '关联的LLM配置ID',
    secondary_llm_id BIGINT NOT NULL COMMENT '辅助LLM配置ID（标题生成、消息压缩使用）',
    workspace_folder VARCHAR(500) COMMENT '工作区目录路径',
    created_at BIGINT NOT NULL COMMENT '创建时间(时间戳毫秒)',
    updated_at BIGINT NOT NULL COMMENT '更新时间(时间戳毫秒)',
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS mc_conversation (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    agent_id BIGINT NOT NULL COMMENT '关联智能体ID',
    title VARCHAR(500) COMMENT '会话标题',
    type VARCHAR(50) DEFAULT 'CHAT' COMMENT '会话类型(CHAT=智能体对话)',
    context_json TEXT COMMENT '会话级上下文状态JSON',
    created_at BIGINT NOT NULL COMMENT '创建时间(时间戳毫秒)',
    updated_at BIGINT NOT NULL COMMENT '更新时间(时间戳毫秒)',
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_mc_conversation_agent_id ON mc_conversation(agent_id);

CREATE TABLE IF NOT EXISTS mc_chat_event_batch (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    conversation_id BIGINT NOT NULL COMMENT '关联会话ID',
    user_content TEXT NOT NULL COMMENT '用户输入内容',
    type VARCHAR(32) NOT NULL DEFAULT 'USER' COMMENT '批次类型(USER=用户任务,CONTEXT_COMPACTION=上下文主动压缩)',
    status VARCHAR(20) NOT NULL DEFAULT 'RUNNING' COMMENT '批次状态(RUNNING=执行中,COMPLETED=已完成,ERROR=出错)',
    error_message TEXT COMMENT '错误信息(仅ERROR状态)',
    input_tokens BIGINT COMMENT '输入token数',
    output_tokens BIGINT COMMENT '输出token数',
    created_at BIGINT NOT NULL COMMENT '创建时间(时间戳毫秒)',
    completed_at BIGINT COMMENT '完成时间(时间戳毫秒)',
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_mc_chat_event_batch_conversation_id ON mc_chat_event_batch(conversation_id);

CREATE TABLE IF NOT EXISTS mc_context_recap (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    conversation_id BIGINT NOT NULL COMMENT '关联会话ID',
    from_batch_id BIGINT NOT NULL COMMENT '覆盖起始批次ID',
    to_batch_id BIGINT NOT NULL COMMENT '覆盖结束批次ID',
    type VARCHAR(32) NOT NULL COMMENT '摘要类型(ROLLING=单批次,ROLLED_UP=合并摘要,PROACTIVE=主动压缩)',
    content TEXT NOT NULL COMMENT '摘要内容',
    created_at BIGINT NOT NULL COMMENT '创建时间(时间戳毫秒)',
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_mc_context_recap_conversation_id ON mc_context_recap(conversation_id);

CREATE TABLE IF NOT EXISTS mc_chat_event (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    batch_id BIGINT NOT NULL COMMENT '关联批次ID',
    event_order INT NOT NULL COMMENT '事件顺序(从0开始)',
    type VARCHAR(20) NOT NULL COMMENT '事件类型(THINKING=思考,TOOL_CALL=工具调用,TOOL_RESULT=工具结果,FINAL_ANSWER=最终回答,ERROR=错误)',
    content TEXT COMMENT '事件内容',
    tool_name VARCHAR(200) COMMENT '工具名称(仅TOOL_CALL/TOOL_RESULT)',
    tool_call_id VARCHAR(100) COMMENT '工具调用ID(仅TOOL_CALL/TOOL_RESULT)',
    tool_arguments TEXT COMMENT '工具参数JSON(仅TOOL_CALL)',
    created_at BIGINT NOT NULL COMMENT '创建时间(时间戳毫秒)',
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_mc_chat_event_batch_id ON mc_chat_event(batch_id);

CREATE TABLE IF NOT EXISTS mc_message (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    conversation_id BIGINT NOT NULL COMMENT '关联会话ID',
    batch_id BIGINT COMMENT '关联批次ID',
    role VARCHAR(20) NOT NULL COMMENT '角色(system/user/assistant/tool)',
    content TEXT NOT NULL COMMENT '消息内容',
    reasoning_content TEXT COMMENT '推理内容(仅assistant消息,Reasoning模型)',
    tool_calls_json TEXT COMMENT '工具调用列表JSON(仅assistant消息)',
    tool_call_id VARCHAR(100) COMMENT '工具调用ID(仅tool消息)',
    input_tokens BIGINT COMMENT '输入token数',
    output_tokens BIGINT COMMENT '输出token数',
    created_at BIGINT NOT NULL COMMENT '创建时间(时间戳毫秒)',
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_mc_message_conversation_id ON mc_message(conversation_id);
CREATE INDEX IF NOT EXISTS idx_mc_message_batch_id ON mc_message(batch_id);

CREATE TABLE IF NOT EXISTS mc_scheduled_task (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    name VARCHAR(255) NOT NULL COMMENT '任务名称',
    agent_id BIGINT NOT NULL COMMENT '关联智能体ID',
    user_prompt TEXT NOT NULL COMMENT '用户提示词',
    cron_expression VARCHAR(100) NOT NULL COMMENT 'Cron表达式',
    create_new_session BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否每次创建新会话(FALSE=绑定同一会话,TRUE=每次新会话)',
    enabled BOOLEAN NOT NULL DEFAULT TRUE COMMENT '是否启用',
    last_status VARCHAR(20) COMMENT '上次执行状态(RUNNING=执行中,SUCCESS=成功,ERROR=失败)',
    last_executed_at BIGINT COMMENT '上次执行时间(时间戳毫秒)',
    created_at BIGINT NOT NULL COMMENT '创建时间(时间戳毫秒)',
    updated_at BIGINT NOT NULL COMMENT '更新时间(时间戳毫秒)',
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS mc_scheduled_task_execution (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    scheduled_task_id BIGINT NOT NULL COMMENT '关联定时任务ID',
    conversation_id BIGINT NOT NULL COMMENT '关联会话ID',
    status VARCHAR(20) NOT NULL COMMENT '执行状态(RUNNING=执行中,SUCCESS=成功,ERROR=失败)',
    executed_at BIGINT NOT NULL COMMENT '执行时间(时间戳毫秒)',
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_scheduled_task_execution_task_id ON mc_scheduled_task_execution(scheduled_task_id);

CREATE TABLE IF NOT EXISTS mc_mcp_service_config (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    name VARCHAR(100) NOT NULL COMMENT '服务名(唯一,作为工具名前缀)',
    description VARCHAR(500) COMMENT '描述',
    protocol VARCHAR(20) NOT NULL COMMENT '协议(STDIO=本地子进程,STREAMABLE_HTTP=流式HTTP,SSE=SSE旧协议)',
    config TEXT NOT NULL COMMENT '协议配置JSON(STDIO:{command,args[],env{}};STREAMABLE_HTTP/SSE:{url,headers{}})',
    enabled BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否启用',
    status VARCHAR(20) NOT NULL DEFAULT 'DISCONNECTED' COMMENT '连接状态(CONNECTED=已连接,DISCONNECTED=未连接,ERROR=连接异常)',
    tools_cache TEXT COMMENT '上次成功连接时发现的工具列表JSON缓存(供前端展示)',
    error_message VARCHAR(1000) COMMENT '最近一次错误信息(status=ERROR时)',
    last_checked_at BIGINT COMMENT '上次状态检查时间戳',
    created_at BIGINT NOT NULL COMMENT '创建时间(时间戳毫秒)',
    updated_at BIGINT NOT NULL COMMENT '更新时间(时间戳毫秒)',
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_mcp_service_config_name ON mc_mcp_service_config(name);

CREATE TABLE IF NOT EXISTS mc_skill_package (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    name VARCHAR(64) NOT NULL COMMENT '技能名(来自SKILL.md frontmatter,作为安装目录名)',
    description VARCHAR(1000) COMMENT '技能描述(来自SKILL.md frontmatter)',
    stored_filename VARCHAR(200) NOT NULL COMMENT '落盘文件名(data/upload/skill-package/下,含时间戳防冲突)',
    original_filename VARCHAR(255) NOT NULL COMMENT '用户上传时的原始文件名',
    file_size BIGINT NOT NULL COMMENT '压缩包字节数',
    created_at BIGINT NOT NULL COMMENT '创建时间(时间戳毫秒)',
    updated_at BIGINT NOT NULL COMMENT '更新时间(时间戳毫秒)',
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS mc_token_usage_log (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    llm_id BIGINT COMMENT '关联LLM配置ID',
    agent_id BIGINT COMMENT '关联智能体ID',
    conversation_id BIGINT COMMENT '关联会话ID',
    batch_id BIGINT COMMENT '关联批次ID',
    model VARCHAR(100) COMMENT '模型名快照(供LLM被删除时回退展示)',
    input_tokens BIGINT NOT NULL DEFAULT 0 COMMENT '输入token数',
    output_tokens BIGINT NOT NULL DEFAULT 0 COMMENT '输出token数',
    total_tokens BIGINT NOT NULL DEFAULT 0 COMMENT '合计token数',
    created_at BIGINT NOT NULL COMMENT '调用时间(时间戳毫秒)',
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_mc_token_usage_log_llm_id ON mc_token_usage_log(llm_id);
CREATE INDEX IF NOT EXISTS idx_mc_token_usage_log_created_at ON mc_token_usage_log(created_at);

