# 模型集成

MeowClaw 将智能体执行模型、辅助模型和向量嵌入模型分开配置：

- **Agent 执行 LLM**：智能体执行任务时使用的主大语言模型。
- **辅助 LLM**：用于生成标题、压缩上下文等辅助工作，通常可选择低成本、响应快的小参数规模模型。
- **向量嵌入模型**：辅助长期记忆的语义存储与召回。

## OpenAI 兼容聊天接口

MeowClaw 支持 OpenAI 兼容协议的 `/v1/chat/completions` 大语言模型接口。大多数模型 Provider 都提供类似端点，因此可以接入自建或第三方模型服务。

### Ollama 示例

Ollama 也提供 OpenAI 兼容端点。若 Ollama 运行在本机默认端口，可将聊天接口地址配置为：

```text
http://localhost:11434/v1/chat/completions
```

随后在该模型配置中填写 Ollama 已提供的模型名称，即可将其用作 Agent 执行 LLM 或辅助 LLM。实际部署时，如果 MeowClaw 与 Ollama 不在同一主机或同一网络命名空间，应将 `localhost` 替换为 MeowClaw 可访问的 Ollama 地址。

## 思考强度（reasoning_effort）

模型配置支持用**有序 JSON 数组**设置 `reasoning_effort` 可选项。全部内置值及其顺序如下：

```json
["none", "minimal", "low", "medium", "high", "xhigh", "max"]
```

除内置值外，也可以加入 Provider 支持的自定义字符串；数组顺序就是对话页选择器中的选项顺序。

### 显示与默认选择规则

- 配置为空时，请求中**不传递** `reasoning_effort`，对话页也不显示思考强度选择器。
- 配置非空时，可以在对话输入框切换本次发送使用的思考强度。
- 默认优先选择 `medium`。
- 如果没有 `medium`，则选择列表中强度最高的内置值。
- 如果列表全部为自定义值，则选择列表最后一项。
- 选择结果按智能体保存在浏览器 `localStorage` 中，同一智能体的新旧聊天会话会自动继承该选择。
- **子智能体与定时任务不沿用对话页的临时选择，而是使用对应模型配置计算出的默认强度。**

## 向量嵌入模型

MeowClaw 支持 OpenAI 兼容协议的 `/v1/embeddings` 向量嵌入模型接口，**最大支持 1024 维向量**。为智能体配置嵌入模型后，长期记忆可使用向量参与语义存储及混合检索召回。

例如，本机默认端口的 Ollama 所对应的 OpenAI 兼容向量接口地址为：

```text
http://localhost:11434/v1/embeddings
```

向量维度不得超过 1024；模型服务本身仍需兼容 OpenAI 的 embeddings 请求与响应格式。
