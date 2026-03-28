package com.gacfox.meowclaw.entity;

import lombok.Data;

@Data
public class TodoItem {
    private Long id;
    private Long conversationId;
    private String text;
    private boolean done;
    private int sortOrder;
    private Long createdAt;
    private Long doneAt;
}
