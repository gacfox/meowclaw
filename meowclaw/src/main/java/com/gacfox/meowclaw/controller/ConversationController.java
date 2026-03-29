package com.gacfox.meowclaw.controller;

import com.gacfox.meowclaw.dto.ApiResponse;
import com.gacfox.meowclaw.dto.ConversationDto;
import com.gacfox.meowclaw.dto.MessageDto;
import com.gacfox.meowclaw.dto.PageDto;
import com.gacfox.meowclaw.service.ConversationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {
    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @GetMapping
    public ApiResponse<PageDto<ConversationDto>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Long agentConfigId,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.success(conversationService.list(agentConfigId, keyword, page, pageSize));
    }

    @GetMapping("/{id}")
    public ApiResponse<ConversationDto> getById(@PathVariable Long id) {
        return ApiResponse.success(conversationService.findById(id));
    }

    @GetMapping("/{id}/messages")
    public ApiResponse<List<MessageDto>> listMessages(@PathVariable Long id) {
        return ApiResponse.success(conversationService.listMessages(id));
    }

    @PostMapping
    public ApiResponse<ConversationDto> create(@Valid @RequestBody ConversationDto dto) {
        return ApiResponse.success(conversationService.create(dto));
    }

    @PutMapping("/{id}")
    public ApiResponse<ConversationDto> update(@PathVariable Long id, @Valid @RequestBody ConversationDto dto) {
        return ApiResponse.success(conversationService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        conversationService.delete(id);
        return ApiResponse.success();
    }

    @PostMapping("/{id}/generate-title")
    public ApiResponse<ConversationDto> generateTitle(@PathVariable Long id) {
        return ApiResponse.success(conversationService.generateTitle(id));
    }

    @DeleteMapping("/{id}/messages/after/{messageId}")
    public ApiResponse<Void> deleteMessagesAfter(@PathVariable Long id, @PathVariable Long messageId) {
        conversationService.deleteMessagesAfter(id, messageId);
        return ApiResponse.success();
    }
}
