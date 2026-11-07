package com.gacfox.meowclaw.controller;

import com.gacfox.meowclaw.dto.*;
import com.gacfox.meowclaw.entity.Conversation;
import com.gacfox.meowclaw.service.ChatService;
import com.gacfox.meowclaw.service.ConversationService;
import com.gacfox.meowclaw.service.GuardrailPolicyService;
import com.gacfox.meowclaw.service.TitleGenerationRegistryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.DeferredResult;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 会话管理
 */
@Tag(name = "会话")
@RestController
@RequestMapping("/api/conversation")
public class ConversationController {
    private static final long TITLE_WAIT_TIMEOUT_MS = 30_000L;

    private final ConversationService conversationService;
    private final ChatService chatService;
    private final TitleGenerationRegistryService titleGenerationRegistryService;
    private final GuardrailPolicyService guardrailPolicyService;

    @Autowired
    public ConversationController(ConversationService conversationService,
                                  ChatService chatService,
                                  TitleGenerationRegistryService titleGenerationRegistryService,
                                  GuardrailPolicyService guardrailPolicyService) {
        this.conversationService = conversationService;
        this.chatService = chatService;
        this.titleGenerationRegistryService = titleGenerationRegistryService;
        this.guardrailPolicyService = guardrailPolicyService;
    }

    @Operation(summary = "查询会话列表")
    @GetMapping
    public ApiResult<Pagination<ConversationDTO>> list(
            @Parameter(description = "智能体 ID") @RequestParam Long agentId,
            @Parameter(description = "会话类型") @RequestParam(required = false) String type,
            @Parameter(description = "是否只查询未分组会话") @RequestParam(defaultValue = "false") boolean ungrouped,
            @Parameter(description = "页码，从 1 开始") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "20") int size) {
        return ApiResult.success(conversationService.listByAgent(agentId, type, ungrouped, page, size));
    }

    @Operation(summary = "创建会话")
    @PostMapping
    public ApiResult<ConversationDTO> create(@RequestBody Map<String, Long> body) {
        return ApiResult.success(conversationService.create(body.get("agentId"), "CHAT", body.get("projectId")));
    }

    @Operation(summary = "查询会话详情")
    @GetMapping("/{id}")
    public ApiResult<ConversationDTO> get(@Parameter(description = "会话 ID") @PathVariable Long id) {
        return ApiResult.success(conversationService.getDTO(id));
    }

    @Operation(summary = "修改会话标题")
    @PutMapping("/{id}/title")
    public ApiResult<ConversationDTO> rename(@Parameter(description = "会话 ID") @PathVariable Long id, @RequestBody @Valid RenameConversationRequest req) {
        return ApiResult.success(conversationService.updateTitle(id, req.getTitle()));
    }

    @Operation(summary = "删除会话")
    @DeleteMapping("/{id}")
    public ApiResult<?> delete(@Parameter(description = "会话 ID") @PathVariable Long id) {
        conversationService.delete(id);
        return ApiResult.success();
    }

    @Operation(summary = "查询历史会话")
    @GetMapping("/history")
    public ApiResult<Pagination<ConversationHistoryDTO>> listHistory(
            @Parameter(description = "会话类型") @RequestParam(required = false) String type,
            @Parameter(description = "智能体 ID") @RequestParam(required = false) Long agentId,
            @Parameter(description = "标题或内容搜索关键词") @RequestParam(required = false) String keyword,
            @Parameter(description = "开始时间戳（毫秒）") @RequestParam(required = false) Long startTime,
            @Parameter(description = "结束时间戳（毫秒）") @RequestParam(required = false) Long endTime,
            @Parameter(description = "页码，从 1 开始") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "20") int size) {
        return ApiResult.success(conversationService.listHistory(type, agentId, keyword, startTime, endTime, page, size));
    }

    @Operation(summary = "批量删除会话")
    @DeleteMapping("/batch")
    public ApiResult<?> deleteBatch(@RequestBody List<Long> ids) {
        conversationService.deleteBatch(ids);
        return ApiResult.success();
    }

    @Operation(summary = "删除全部会话")
    @DeleteMapping("/all")
    public ApiResult<?> deleteAll() {
        conversationService.deleteAll();
        return ApiResult.success();
    }

    @Operation(summary = "查询会话消息批次")
    @GetMapping("/{id}/batch")
    public ApiResult<List<ChatEventBatchDTO>> listBatches(@Parameter(description = "会话 ID") @PathVariable Long id) {
        return ApiResult.success(conversationService.listBatches(id));
    }

    @Operation(summary = "截断会话消息")
    @DeleteMapping("/{id}/batch/{batchId}/truncate")
    public ApiResult<?> truncateAfterBatch(
            @Parameter(description = "会话 ID") @PathVariable Long id,
            @Parameter(description = "消息批次 ID") @PathVariable Long batchId,
            @Parameter(description = "是否同时删除指定批次") @RequestParam(defaultValue = "false") boolean includeSelf) {
        conversationService.truncateAfterBatch(id, batchId, includeSelf);
        return ApiResult.success();
    }

    @Operation(summary = "发送消息并流式返回响应")
    @PostMapping(value = "/{id}/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ChatEventDTO> chat(@Parameter(description = "会话 ID") @PathVariable Long id, @RequestBody SendMessageRequest req) {
        return chatService.chat(id, req.getContent(), req.getImages(), req.getReasoningEffort());
    }

    @Operation(summary = "订阅会话执行事件")
    @GetMapping(value = "/{id}/watch", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ChatEventDTO> watch(@Parameter(description = "会话 ID") @PathVariable Long id) {
        return chatService.watch(id);
    }

    @Operation(summary = "查询运行中的会话 ID")
    @GetMapping("/running-ids")
    public ApiResult<List<Long>> runningIds() {
        return ApiResult.success(chatService.runningConversationIds());
    }

    @Operation(summary = "查询侧边栏会话分组")
    @GetMapping("/sidebar-groups")
    public ApiResult<SidebarGroupsDTO> sidebarGroups(@Parameter(description = "智能体 ID") @RequestParam Long agentId) {
        return ApiResult.success(conversationService.sidebarGroups(agentId));
    }

    @Operation(summary = "停止会话执行")
    @PostMapping("/{id}/stop")
    public ApiResult<?> stop(@Parameter(description = "会话 ID") @PathVariable Long id) {
        return chatService.stop(id) ? ApiResult.success() : ApiResult.failure("会话未在执行中");
    }

    @Operation(summary = "更新会话安全策略")
    @PutMapping("/{id}/guardrail-policy")
    public ApiResult<ConversationDTO> updateGuardrailPolicy(@Parameter(description = "会话 ID") @PathVariable Long id, @RequestBody Map<String, Long> body) {
        guardrailPolicyService.updateConversationPolicy(id, body.get("policyId"));
        return ApiResult.success(conversationService.getDTO(id));
    }

    @Operation(summary = "设置会话置顶状态")
    @PutMapping("/{id}/pin")
    public ApiResult<ConversationDTO> pin(@Parameter(description = "会话 ID") @PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        return ApiResult.success(conversationService.pin(id, Boolean.TRUE.equals(body.get("pinned"))));
    }

    @Operation(summary = "移动会话到项目")
    @PutMapping("/{id}/project")
    public ApiResult<ConversationDTO> moveToProject(@Parameter(description = "会话 ID") @PathVariable Long id, @RequestBody Map<String, Long> body) {
        return ApiResult.success(conversationService.moveToProject(id, body.get("projectId")));
    }

    @Operation(summary = "审批工具调用")
    @PostMapping("/{id}/approvals/{toolCallId}")
    public ApiResult<?> decideApproval(@Parameter(description = "会话 ID") @PathVariable Long id,
                                       @Parameter(description = "工具调用 ID") @PathVariable String toolCallId,
                                       @RequestBody Map<String, String> body) {
        boolean approved = "approve".equals(body.get("decision"));
        return chatService.decideApproval(id, toolCallId, approved)
                ? ApiResult.success() : ApiResult.failure("审批不存在或已处理");
    }

    @Operation(summary = "等待会话标题生成")
    @GetMapping("/{id}/title-wait")
    public DeferredResult<ApiResult<Map<String, String>>> waitTitle(@Parameter(description = "会话 ID") @PathVariable Long id) {
        DeferredResult<ApiResult<Map<String, String>>> result = new DeferredResult<>(TITLE_WAIT_TIMEOUT_MS);
        result.onTimeout(() -> result.setResult(ApiResult.success(Map.of("title", ""))));

        CompletableFuture<String> future = titleGenerationRegistryService.get(id);
        if (future == null) {
            Conversation conv = conversationService.getById(id);
            String currentTitle = conv.getTitle() == null ? "" : conv.getTitle();
            result.setResult(ApiResult.success(Map.of("title", currentTitle)));
            return result;
        }
        future.whenComplete((title, ex) -> {
            if (ex != null) {
                result.setResult(ApiResult.success(Map.of("title", "")));
            } else {
                result.setResult(ApiResult.success(Map.of("title", title == null ? "" : title)));
            }
        });
        return result;
    }
}
