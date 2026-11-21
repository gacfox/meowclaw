# MeowClaw 喵爪智能体

可自托管的单用户 ReAct 风格通用智能体工具。

![](https://github.com/user-attachments/assets/03560a20-b305-4e16-8f44-4e35805439ec)

## 技术栈

- 后端：Java、Spring Boot、H2、Lucene
- 前端：React、Vite、Tailwind CSS、shadcn/ui

## 核心特性

- **智能体配置**：自定义人设、模型、工作区、内置工具与 MCP 工具。
- **多轮对话**：支持长流程 ReAct 执行、上下文压缩、会话钉选和项目管理。
- **多模态输入**：使用具备视觉能力的模型处理聊天及文件中的图片。
- **工具与扩展**：内置文件、搜索、Shell 等工具，支持 MCP 和 Agent Skill。
- **长期记忆**：通过全文与向量混合检索，在跨会话任务中复用记忆。
- **子智能体**：支持通用子任务和具有独立配置的专业子智能体。
- **安全护栏**：按策略放行、拒绝或审批工具调用。
- **定时任务**：使用 Cron 表达式自动向智能体派发自然语言任务。
- **观测与维护**：提供 Token 统计、LLM 调用追踪、资源监控及配置迁移。

## 构建部署

### 环境要求

- JDK 21
- Maven
- Node.js 与 npm

### 1. 安装 Agent 框架依赖

MeowClaw 使用轻量级 Agent 框架 `proarc-agentic`，构建前需要先安装到本地 Maven 仓库：

```bash
git clone https://github.com/gacfox/proarc-agentic
cd proarc-agentic
mvn clean install
```

### 2. 构建并启动 MeowClaw

```bash
# 构建前端
cd meowclaw-web
npm install
npm run build

# 构建后端，前端资源会自动打入 jar
cd ../meowclaw
mvn clean package

# 启动服务
cd target
java -jar meowclaw-0.0.1-SNAPSHOT.jar
```

启动后访问 <http://127.0.0.1:8080>，首次使用时创建管理员账户。前后端共用同一个服务，无需单独部署前端或配置 Nginx。

默认数据目录为运行目录下的 `data`，可通过环境变量 `MEOWCLAW_DATA_DIR` 修改。更多部署和配置说明见[配置与部署](https://gacfox.github.io/meowclaw/deployment.html)。

## 安全提醒

不建议将 MeowClaw 直接暴露到公网。智能体可以操作文件并执行系统命令，尤其在启用全部工具及「无限制模式」时权限很高；部署到云主机时，推荐使用 SSH 隧道访问，并妥善保管包含明文密钥的配置导出文件。
