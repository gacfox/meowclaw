package com.gacfox.meowclaw.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gacfox.meowclaw.agent.tool.Tool;
import com.gacfox.meowclaw.agent.tool.ToolExecutionContext;
import com.gacfox.meowclaw.dto.ChatStreamEventDto;
import com.gacfox.meowclaw.dto.MessageDto;
import com.gacfox.meowclaw.entity.AgentConfig;
import com.gacfox.meowclaw.entity.LlmConfig;
import com.gacfox.meowclaw.service.TodoService;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.JsonValue;
import com.openai.core.http.StreamResponse;
import com.openai.helpers.ChatCompletionAccumulator;
import com.openai.models.FunctionDefinition;
import com.openai.models.FunctionParameters;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionChunk;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionMessage;
import com.openai.models.chat.completions.ChatCompletionAssistantMessageParam;
import com.openai.models.chat.completions.ChatCompletionMessageFunctionToolCall;
import com.openai.models.chat.completions.ChatCompletionMessageParam;
import com.openai.models.chat.completions.ChatCompletionMessageToolCall;
import com.openai.models.chat.completions.ChatCompletionSystemMessageParam;
import com.openai.models.chat.completions.ChatCompletionToolMessageParam;
import com.openai.models.chat.completions.ChatCompletionUserMessageParam;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
public class ReActAgent {
    private final AgentConfig agentConfig;
    private final LlmConfig llmConfig;
    private final List<MessageDto> conversationHistory;
    private final List<Tool> tools;
    private final ObjectMapper objectMapper;
    private final OpenAIClient openAIClient;
    private final ToolExecutionContext toolExecutionContext;
    private final Long conversationId;
    private final TodoService todoService;

    public ReActAgent(AgentConfig agentConfig,
                      LlmConfig llmConfig,
                      List<MessageDto> conversationHistory,
                      List<Tool> tools,
                      String workspaceBaseDir,
                      Long conversationId,
                      TodoService todoService) {
        this.agentConfig = agentConfig;
        this.llmConfig = llmConfig;
        this.conversationHistory = conversationHistory;
        this.tools = tools;
        this.objectMapper = new ObjectMapper();
        OpenAIOkHttpClient.Builder clientBuilder = OpenAIOkHttpClient.builder()
                .baseUrl(llmConfig.getApiUrl())
                .timeout(Duration.ofSeconds(60));
        if (llmConfig.getApiKey() != null && !llmConfig.getApiKey().isBlank()) {
            clientBuilder.apiKey(llmConfig.getApiKey());
        }
        this.openAIClient = clientBuilder.build();
        this.conversationId = conversationId;
        this.todoService = todoService;
        this.toolExecutionContext = new ToolExecutionContext(
                agentConfig,
                resolveWorkspaceDir(agentConfig, workspaceBaseDir),
                conversationId
        );
    }

    public Flux<ChatStreamEventDto> chat(String userMessage) {
        Sinks.Many<ChatStreamEventDto> sink = Sinks.many().unicast().onBackpressureBuffer();

        MessageDto userMsg = new MessageDto();
        userMsg.setRole(MessageDto.ROLE_USER);
        userMsg.setContent(userMessage);
        userMsg.setTimestamp(Instant.now().toEpochMilli());
        userMsg.setInputTokens(0L);
        userMsg.setOutputTokens(0L);
        userMsg.setApiUrl(llmConfig.getApiUrl());
        userMsg.setModel(llmConfig.getModel());
        conversationHistory.add(userMsg);

        Mono.fromRunnable(() -> processChat(sink))
                .subscribeOn(Schedulers.boundedElastic())
                .doFinally(signal -> sink.tryEmitComplete())
                .subscribe();
        return sink.asFlux();
    }

    private void processChat(Sinks.Many<ChatStreamEventDto> sink) {
        try {
            log.info("开始ReAct循环，可用工具数量: {}", tools.size());

            long totalInputTokens = 0L;
            long totalOutputTokens = 0L;
            List<ChatCompletionMessageParam> messages = buildMessagesFromHistory();

            while (true) {
                ChatCompletion completion = callLLM(messages);
                TokenUsage usage = extractUsage(completion);
                if (usage != null) {
                    totalInputTokens += usage.inputTokens();
                    totalOutputTokens += usage.outputTokens();
                }
                ChatCompletionMessage message = completion.choices().get(0).message();

                if (message.toolCalls().isPresent() && !message.toolCalls().get().isEmpty()) {
                    List<ChatCompletionMessageToolCall> toolCalls = message.toolCalls().get();
                    messages.add(ChatCompletionMessageParam.ofAssistant(message.toParam()));

                    for (ChatCompletionMessageToolCall toolCall : toolCalls) {
                        if (!toolCall.isFunction()) {
                            continue;
                        }
                        ChatCompletionMessageFunctionToolCall functionToolCall = toolCall.asFunction();
                        String toolName = functionToolCall.function().name();
                        String toolArgs = functionToolCall.function().arguments();

                        emitEvent(sink, ChatStreamEventDto.TYPE_TOOL_CALL,
                                buildToolEventPayload(toolName, toolArgs, null));

                        String observation = executeTool(toolName, toolArgs);
                        emitEvent(sink, ChatStreamEventDto.TYPE_TOOL_RESULT,
                                buildToolEventPayload(toolName, null, observation));

                        MessageDto toolRecord = new MessageDto();
                        toolRecord.setRole(MessageDto.ROLE_TOOL);
                        toolRecord.setContent(buildToolStorePayload(toolName, toolArgs, observation));
                        toolRecord.setTimestamp(Instant.now().toEpochMilli());
                        toolRecord.setInputTokens(0L);
                        toolRecord.setOutputTokens(0L);
                        toolRecord.setApiUrl(llmConfig.getApiUrl());
                        toolRecord.setModel(llmConfig.getModel());
                        conversationHistory.add(toolRecord);

                        ChatCompletionToolMessageParam toolMessage = ChatCompletionToolMessageParam.builder()
                                .toolCallId(functionToolCall.id())
                                .content(observation)
                                .build();
                        messages.add(ChatCompletionMessageParam.ofTool(toolMessage));
                    }

                    continue;
                }

                StreamResult streamResult = streamContentResponse(messages, sink);
                String content = streamResult.content();
                totalInputTokens += streamResult.inputTokens();
                totalOutputTokens += streamResult.outputTokens();

                MessageDto assistantMsg = new MessageDto();
                assistantMsg.setRole(MessageDto.ROLE_ASSISTANT);
                assistantMsg.setContent(content);
                assistantMsg.setTimestamp(Instant.now().toEpochMilli());
                assistantMsg.setInputTokens(totalInputTokens);
                assistantMsg.setOutputTokens(totalOutputTokens);
                assistantMsg.setApiUrl(llmConfig.getApiUrl());
                assistantMsg.setModel(llmConfig.getModel());
                conversationHistory.add(assistantMsg);

                emitEvent(sink, ChatStreamEventDto.TYPE_FINISH, "");
                return;
            }
        } catch (Exception e) {
            log.error("ReAct处理失败", e);
            emitEvent(sink, ChatStreamEventDto.TYPE_ERROR, "处理失败: " + e.getMessage());
            emitEvent(sink, ChatStreamEventDto.TYPE_FINISH, "");
        }
    }

    private ChatCompletionCreateParams buildCompletionParams(List<ChatCompletionMessageParam> messages) {
        ChatCompletionCreateParams.Builder paramsBuilder = ChatCompletionCreateParams.builder()
                .model(llmConfig.getModel())
                .messages(messages);

        if (llmConfig.getTemperature() != null) {
            paramsBuilder.temperature(llmConfig.getTemperature());
        }
        if (llmConfig.getMaxContextLength() != null) {
            paramsBuilder.maxTokens(llmConfig.getMaxContextLength() / 2);
        }
        enableStreamUsage(paramsBuilder);

        List<FunctionDefinition> functionDefinitions = buildFunctionDefinitions();
        for (FunctionDefinition functionDefinition : functionDefinitions) {
            paramsBuilder.addFunctionTool(functionDefinition);
        }

        return paramsBuilder.build();
    }

    private ChatCompletion callLLM(List<ChatCompletionMessageParam> messages) {
        try {
            return openAIClient.chat().completions().create(buildCompletionParams(messages));
        } catch (Exception e) {
            log.error("LLM调用失败", e);
            throw new RuntimeException("LLM调用失败: " + e.getMessage(), e);
        }
    }

    private StreamResult streamContentResponse(List<ChatCompletionMessageParam> messages, Sinks.Many<ChatStreamEventDto> sink) {
        StringBuilder contentBuilder = new StringBuilder();
        ChatCompletionAccumulator accumulator = ChatCompletionAccumulator.create();
        java.util.concurrent.atomic.AtomicReference<TokenUsage> usageRef = new java.util.concurrent.atomic.AtomicReference<>();

        try (StreamResponse<ChatCompletionChunk> streamResponse = openAIClient.chat().completions().createStreaming(buildCompletionParams(messages))) {
            streamResponse.stream()
                    .peek(accumulator::accumulate)
                    .peek(chunk -> {
                        TokenUsage usage = extractUsage(chunk);
                        if (usage != null) {
                            usageRef.set(usage);
                        }
                    })
                    .flatMap(completion -> completion.choices().stream())
                    .flatMap(choice -> choice.delta().content().stream())
                    .forEach(delta -> {
                        contentBuilder.append(delta);
                        emitEvent(sink, ChatStreamEventDto.TYPE_CONTENT, delta);
                    });
        } catch (Exception e) {
            log.error("流式LLM调用失败", e);
            emitEvent(sink, ChatStreamEventDto.TYPE_ERROR, "流式响应失败: " + e.getMessage());
        }
        TokenUsage usage = usageRef.get();
        if (usage == null) {
            usage = extractUsage(accumulator);
        }
        if (usage == null) {
            usage = new TokenUsage(0L, 0L);
        }
        return new StreamResult(contentBuilder.toString(), usage.inputTokens(), usage.outputTokens());
    }

    private String buildSystemPrompt() {
        StringBuilder prompt = new StringBuilder();

        prompt.append("你是一个智能助手，可以使用工具来帮助用户解决问题。\n\n");
        prompt.append("Agent: ").append(agentConfig.getName()).append("\n");

        if (agentConfig.getSystemPrompt() != null && !agentConfig.getSystemPrompt().isEmpty()) {
            prompt.append("\n").append(agentConfig.getSystemPrompt()).append("\n");
        }

        if (conversationId != null && todoService != null) {
            String todoSection = todoService.formatTodos(conversationId);
            if (!todoSection.isEmpty()) {
                prompt.append("\n").append(todoSection);
            }
        }

        return prompt.toString();
    }

    private List<ChatCompletionMessageParam> buildMessagesFromHistory() {
        List<ChatCompletionMessageParam> messages = new ArrayList<>();
        messages.add(ChatCompletionMessageParam.ofSystem(
                ChatCompletionSystemMessageParam.builder().content(buildSystemPrompt()).build()
        ));

        int startIdx = Math.max(0, conversationHistory.size() - 20);
        for (int i = startIdx; i < conversationHistory.size(); i++) {
            MessageDto msg = conversationHistory.get(i);
            switch (msg.getRole()) {
                case MessageDto.ROLE_USER -> messages.add(ChatCompletionMessageParam.ofUser(
                        ChatCompletionUserMessageParam.builder().content(msg.getContent()).build()
                ));
                case MessageDto.ROLE_ASSISTANT -> messages.add(ChatCompletionMessageParam.ofAssistant(
                        ChatCompletionAssistantMessageParam.builder().content(msg.getContent()).build()
                ));
                default -> {
                }
            }
        }

        return messages;
    }

    private List<FunctionDefinition> buildFunctionDefinitions() {
        if (tools.isEmpty()) {
            return List.of();
        }

        return tools.stream()
                .map(tool -> FunctionDefinition.builder()
                        .name(tool.getName())
                        .description(tool.getDescription())
                        .parameters(parseParameters(tool.getParameters()))
                        .build())
                .collect(Collectors.toList());
    }

    private FunctionParameters parseParameters(String parametersJson) {
        if (parametersJson == null || parametersJson.isBlank()) {
            return FunctionParameters.builder().build();
        }
        try {
            JsonNode schemaNode = objectMapper.readTree(parametersJson);
            HashMap<String, JsonValue> props = new HashMap<>();
            schemaNode.fields().forEachRemaining(entry ->
                    props.put(entry.getKey(), JsonValue.fromJsonNode(entry.getValue())));
            return FunctionParameters.builder()
                    .additionalProperties(props)
                    .build();
        } catch (Exception e) {
            log.warn("解析工具参数失败: {}", parametersJson, e);
            return FunctionParameters.builder().build();
        }
    }

    private String executeTool(String toolName, String params) {
        Optional<Tool> toolOpt = tools.stream()
                .filter(t -> t.getName().equals(toolName))
                .findFirst();

        if (toolOpt.isEmpty()) {
            return String.format("错误: 未找到工具 '%s'", toolName);
        }

        try {
            Tool tool = toolOpt.get();
            return tool.execute(params, toolExecutionContext).block();
        } catch (Exception e) {
            log.error("工具执行失败: {}", toolName, e);
            return String.format("工具执行失败: %s", e.getMessage());
        }
    }

    private Path resolveWorkspaceDir(AgentConfig agent, String workspaceBaseDir) {
        String baseDir = (workspaceBaseDir == null || workspaceBaseDir.isBlank())
                ? "./data/workspaces"
                : workspaceBaseDir;
        String folder = agent.getWorkspaceFolder();
        if (folder == null || folder.isBlank()) {
            folder = agent.getName();
        }
        try {
            Path workspaceDir = Paths.get(baseDir).resolve(folder).toAbsolutePath().normalize();
            Files.createDirectories(workspaceDir);
            return workspaceDir;
        } catch (Exception e) {
            log.warn("创建工作区失败: {}", e.getMessage());
            return Paths.get(baseDir).toAbsolutePath().normalize();
        }
    }

    private void emitEvent(Sinks.Many<ChatStreamEventDto> sink, String type, String content) {
        ChatStreamEventDto event = new ChatStreamEventDto();
        event.setType(type);
        event.setContent(content);
        event.setTimestamp(Instant.now().toEpochMilli());
        sink.tryEmitNext(event);
    }

    private String buildToolEventPayload(String toolName, String args, String result) {
        try {
            HashMap<String, String> payload = new HashMap<>();
            payload.put("toolName", toolName);
            if (args != null) {
                payload.put("args", args);
            }
            if (result != null) {
                payload.put("result", result);
            }
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String buildToolStorePayload(String toolName, String args, String result) {
        return buildToolEventPayload(toolName, args, result);
    }

    private void enableStreamUsage(ChatCompletionCreateParams.Builder paramsBuilder) {
        try {
            Class<?> optionsClass = Class.forName("com.openai.models.chat.completions.ChatCompletionStreamOptions");
            Object optionsBuilder = optionsClass.getMethod("builder").invoke(null);
            try {
                optionsBuilder.getClass().getMethod("includeUsage", boolean.class)
                        .invoke(optionsBuilder, true);
            } catch (NoSuchMethodException e) {
                optionsBuilder.getClass().getMethod("includeUsage", Boolean.class)
                        .invoke(optionsBuilder, true);
            }
            Object options = optionsBuilder.getClass().getMethod("build").invoke(optionsBuilder);
            paramsBuilder.getClass().getMethod("streamOptions", optionsClass).invoke(paramsBuilder, options);
        } catch (Exception ignored) {
            // ignore
        }
    }

    private TokenUsage extractUsage(Object source) {
        if (source == null) {
            return null;
        }
        Object usage = invokeMethod(source, "usage");
        if (usage == null) {
            usage = invokeMethod(source, "getUsage");
        }
        if (usage instanceof java.util.Optional<?> opt) {
            usage = opt.orElse(null);
        }
        if (usage == null) {
            return null;
        }
        Long promptTokens = readUsageLong(usage,
                "promptTokens",
                "prompt_tokens",
                "inputTokens",
                "input_tokens");
        Long completionTokens = readUsageLong(usage,
                "completionTokens",
                "completion_tokens",
                "outputTokens",
                "output_tokens");
        if (promptTokens == null && completionTokens == null) {
            return null;
        }
        return new TokenUsage(promptTokens == null ? 0L : promptTokens,
                completionTokens == null ? 0L : completionTokens);
    }

    private Object invokeMethod(Object target, String methodName) {
        try {
            return target.getClass().getMethod(methodName).invoke(target);
        } catch (Exception e) {
            return null;
        }
    }

    private Long readUsageLong(Object usage, String... methodNames) {
        for (String methodName : methodNames) {
            try {
                Object value = usage.getClass().getMethod(methodName).invoke(usage);
                if (value instanceof Number number) {
                    return number.longValue();
                }
            } catch (Exception ignored) {
                // ignore
            }
        }
        return null;
    }

    private record TokenUsage(long inputTokens, long outputTokens) {
    }

    private record StreamResult(String content, long inputTokens, long outputTokens) {
    }
}
