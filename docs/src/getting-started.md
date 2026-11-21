# 快速开始

MeowClaw（喵爪智能体）是一个可自托管的单用户 ReAct 风格通用智能体工具。源码分为 `meowclaw-web` 前端和 `meowclaw` 后端，构建完成后可由一个 jar 同时提供前后端服务。

## 环境准备

构建前请准备好：

- JDK 21（后端编译目标为 Java 21）
- Maven
- Node.js 与 npm

## 安装 proarc-agentic 依赖

MeowClaw 使用轻量级 Agent 框架 `proarc-agentic`，后端依赖其中的 `proarc-agentic-spring-boot-starter`（项目配置版本为 `1.1-SNAPSHOT`）。构建 MeowClaw 前，需要先下载该框架并安装到本地 Maven 仓库：

```bash
git clone https://github.com/gacfox/proarc-agentic
cd proarc-agentic
mvn clean install
```

## 构建前端

回到 MeowClaw 源码根目录，安装依赖并构建前端：

```bash
cd meowclaw-web
npm install
npm run build
```

构建结果生成在 `meowclaw-web/dist`。

## 构建并启动后端

回到源码根目录后执行：

```bash
mvn -f meowclaw/pom.xml clean package
java -jar meowclaw/target/meowclaw-0.0.1-SNAPSHOT.jar
```

后端构建会自动将前端 `dist` 复制进 jar 的 `static` 目录，因此启动后无需再单独运行前端。服务默认监听 `127.0.0.1:8080`，在浏览器访问：

```text
http://127.0.0.1:8080
```

## 首次创建管理员

MeowClaw 是单用户系统。首次使用时，请按页面提示创建管理员账户；管理员具备所有操作权限。后续可以在后台修改管理员用户名和密码。
