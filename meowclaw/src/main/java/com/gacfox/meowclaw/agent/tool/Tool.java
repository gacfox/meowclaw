package com.gacfox.meowclaw.agent.tool;

import reactor.core.publisher.Mono;

public interface Tool {
    String getName();

    String getDescription();

    String getParameters();

    Mono<String> execute(String params, ToolExecutionContext context);
}
