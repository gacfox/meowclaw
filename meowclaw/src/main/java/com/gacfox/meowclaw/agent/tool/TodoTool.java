package com.gacfox.meowclaw.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gacfox.meowclaw.entity.TodoItem;
import com.gacfox.meowclaw.service.TodoService;
import com.gacfox.meowclaw.util.SpringContextUtil;
import reactor.core.publisher.Mono;

public class TodoTool implements Tool {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String NAME = "todo";
    private static final String DESCRIPTION = "管理待办事项：添加、修改、完成、清空。任务板与会话绑定。";
    private static final String PARAMETERS = """
            {
              "type": "object",
              "properties": {
                "action": {
                  "type": "string",
                  "description": "操作类型：add、update、done、clear",
                  "enum": ["add", "update", "done", "clear"]
                },
                "text": {
                  "type": "string",
                  "description": "待办内容（action=add或update时必填）"
                },
                "id": {
                  "type": "integer",
                  "description": "待办ID（action=update或done时必填）"
                }
              },
              "required": ["action"]
            }
            """;

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return DESCRIPTION;
    }

    @Override
    public String getParameters() {
        return PARAMETERS;
    }

    @Override
    public Mono<String> execute(String params, ToolExecutionContext context) {
        return Mono.fromCallable(() -> {
            Long conversationId = context.getConversationId();
            TodoService todoService = SpringContextUtil.getBean(TodoService.class);

            if (conversationId == null) {
                return "错误：会话ID不存在";
            }

            JsonNode node = OBJECT_MAPPER.readTree(params);
            String action = node.get("action").asText();

            switch (action) {
                case "add" -> {
                    String text = node.has("text") ? node.get("text").asText() : null;
                    if (text == null || text.isBlank()) {
                        return "待办内容不能为空";
                    }
                    TodoItem item = todoService.addTodo(conversationId, text);
                    return "已添加待办 #" + item.getId() + ": " + item.getText();
                }
                case "update" -> {
                    if (!node.has("id")) {
                        return "请提供待办ID";
                    }
                    String newText = node.has("text") ? node.get("text").asText() : null;
                    if (newText == null || newText.isBlank()) {
                        return "待办内容不能为空";
                    }
                    long id = node.get("id").asLong();
                    try {
                        TodoItem item = todoService.updateTodo(conversationId, id, newText);
                        return "已更新待办 #" + item.getId() + ": " + item.getText();
                    } catch (IllegalArgumentException e) {
                        return e.getMessage();
                    }
                }
                case "done" -> {
                    if (!node.has("id")) {
                        return "请提供待办ID";
                    }
                    long id = node.get("id").asLong();
                    try {
                        TodoItem item = todoService.doneTodo(conversationId, id);
                        return "已完成待办 #" + item.getId() + ": " + item.getText();
                    } catch (IllegalArgumentException e) {
                        return e.getMessage();
                    }
                }
                case "clear" -> {
                    todoService.clearTodos(conversationId);
                    return "已清空任务板";
                }
                default -> {
                    return "未知操作: " + action;
                }
            }
        });
    }
}
