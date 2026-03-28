package com.gacfox.meowclaw.service;

import com.gacfox.meowclaw.entity.TodoItem;
import com.gacfox.meowclaw.repository.TodoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class TodoService {
    private final TodoRepository todoRepository;

    public TodoService(TodoRepository todoRepository) {
        this.todoRepository = todoRepository;
    }

    public List<TodoItem> getTodos(Long conversationId) {
        return todoRepository.findByConversationId(conversationId);
    }

    public boolean hasTodos(Long conversationId) {
        return todoRepository.countByConversationId(conversationId) > 0;
    }

    @Transactional
    public TodoItem addTodo(Long conversationId, String text) {
        TodoItem item = new TodoItem();
        item.setConversationId(conversationId);
        item.setText(text.trim());
        item.setDone(false);
        item.setSortOrder(todoRepository.getMaxSortOrder(conversationId) + 1);
        item.setCreatedAt(Instant.now().toEpochMilli());
        return todoRepository.save(item);
    }

    @Transactional
    public TodoItem updateTodo(Long conversationId, Long todoId, String newText) {
        List<TodoItem> items = todoRepository.findByConversationId(conversationId);
        TodoItem item = items.stream()
                .filter(i -> i.getId().equals(todoId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未找到待办ID: " + todoId));
        item.setText(newText.trim());
        return todoRepository.save(item);
    }

    @Transactional
    public TodoItem doneTodo(Long conversationId, Long todoId) {
        List<TodoItem> items = todoRepository.findByConversationId(conversationId);
        TodoItem item = items.stream()
                .filter(i -> i.getId().equals(todoId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未找到待办ID: " + todoId));
        if (!item.isDone()) {
            item.setDone(true);
            item.setDoneAt(Instant.now().toEpochMilli());
            todoRepository.save(item);
        }
        return item;
    }

    @Transactional
    public void clearTodos(Long conversationId) {
        todoRepository.deleteByConversationId(conversationId);
    }

    public String formatTodos(Long conversationId) {
        List<TodoItem> items = todoRepository.findByConversationId(conversationId);
        if (items.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("## 当前任务板\n");
        for (TodoItem item : items) {
            builder.append("#").append(item.getId())
                    .append(item.isDone() ? " [x] " : " [ ] ")
                    .append(item.getText())
                    .append("\n");
        }
        return builder.toString();
    }
}
