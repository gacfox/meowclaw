package com.gacfox.meowclaw.dto;

import lombok.Data;

@Data
public class ChatStreamEventDto {
    public static final String TYPE_CONTENT = "content";
    public static final String TYPE_THOUGHT = "thought";
    public static final String TYPE_ACTION = "action";
    public static final String TYPE_OBSERVATION = "observation";
    public static final String TYPE_TOOL_CALL = "tool_call";
    public static final String TYPE_TOOL_RESULT = "tool_result";
    public static final String TYPE_FINISH = "finish";
    public static final String TYPE_ERROR = "error";

    private String type;
    private String content;
    private Long timestamp;
}
