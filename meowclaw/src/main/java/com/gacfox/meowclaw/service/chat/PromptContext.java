package com.gacfox.meowclaw.service.chat;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PromptContext {
    private String content;
    private List<Long> recapIds;
}
