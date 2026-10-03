package com.gacfox.meowclaw.controller;

import com.gacfox.meowclaw.dto.ChatEventBatchDTO;
import com.gacfox.meowclaw.dto.ChatEventDTO;
import com.gacfox.meowclaw.dto.ConversationDTO;
import com.gacfox.meowclaw.dto.ConversationHistoryDTO;
import com.gacfox.meowclaw.dto.RenameConversationRequest;
import com.gacfox.meowclaw.dto.SendMessageRequest;
import com.gacfox.meowclaw.entity.Conversation;
import com.gacfox.meowclaw.service.ChatService;
import com.gacfox.meowclaw.service.ConversationService;
import com.gacfox.meowclaw.service.GuardrailPolicyService;
import com.gacfox.meowclaw.service.TitleGenerationRegistryService;
import com.gacfox.meowclaw.dto.ApiResult;
import com.gacfox.meowclaw.dto.Pagination;
import com.gacfox.meowclaw.dto.SidebarGroupsDTO;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

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

    @GetMapping
    public ApiResult<Pagination<ConversationDTO>> list(
            @RequestParam Long agentId,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "false") boolean ungrouped,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResult.success(conversationService.listByAgent(agentId, type, ungrouped, page, size));
    }

    @PostMapping
    public ApiResult<ConversationDTO> create(@RequestBody Map<String, Long> body) {
        return ApiResult.success(conversationService.create(body.get("agentId"), "CHAT", body.get("projectId")));
    }

    @GetMapping("/{id}")
    public ApiResult<ConversationDTO> get(@PathVariable Long id) {
        return ApiResult.success(conversationService.getDTO(id));
    }

    @PutMapping("/{id}/title")
    public ApiResult<ConversationDTO> rename(@PathVariable Long id, @RequestBody @Valid RenameConversationRequest req) {
        return ApiResult.success(conversationService.updateTitle(id, req.getTitle()));
    }

    @DeleteMapping("/{id}")
    public ApiResult<?> delete(@PathVariable Long id) {
        conversationService.delete(id);
        return ApiResult.success();
    }

    @GetMapping("/history")
    public ApiResult<Pagination<ConversationHistoryDTO>> listHistory(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Long agentId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long startTime,
            @RequestParam(required = false) Long endTime,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResult.success(conversationService.listHistory(type, agentId, keyword, startTime, endTime, page, size));
    }

    @DeleteMapping("/batch")
    public ApiResult<?> deleteBatch(@RequestBody List<Long> ids) {
        conversationService.deleteBatch(ids);
        return ApiResult.success();
    }

    @DeleteMapping("/all")
    public ApiResult<?> deleteAll() {
        conversationService.deleteAll();
        return ApiResult.success();
    }

    @GetMapping("/{id}/batch")
    public ApiResult<List<ChatEventBatchDTO>> listBatches(@PathVariable Long id) {
        return ApiResult.success(conversationService.listBatches(id));
    }

    @DeleteMapping("/{id}/batch/{batchId}/truncate")
    public ApiResult<?> truncateAfterBatch(
            @PathVariable Long id,
            @PathVariable Long batchId,
            @RequestParam(defaultValue = "false") boolean includeSelf) {
        conversationService.truncateAfterBatch(id, batchId, includeSelf);
        return ApiResult.success();
    }

    @PostMapping(value = "/{id}/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ChatEventDTO> chat(@PathVariable Long id, @RequestBody SendMessageRequest req) {
        return chatService.chat(id, req.getContent(), req.getImages());
    }

    @GetMapping(value = "/{id}/watch", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ChatEventDTO> watch(@PathVariable Long id) {
        return chatService.watch(id);
    }

    @GetMapping("/running-ids")
    public ApiResult<List<Long>> runningIds() {
        return ApiResult.success(chatService.runningConversationIds());
    }

    @GetMapping("/sidebar-groups")
    public ApiResult<SidebarGroupsDTO> sidebarGroups(@RequestParam Long agentId) {
        return ApiResult.success(conversationService.sidebarGroups(agentId));
    }

    @PostMapping("/{id}/stop")
    public ApiResult<?> stop(@PathVariable Long id) {
        return chatService.stop(id) ? ApiResult.success() : ApiResult.failure("会话未在执行中");
    }

    @PutMapping("/{id}/guardrail-policy")
    public ApiResult<ConversationDTO> updateGuardrailPolicy(@PathVariable Long id, @RequestBody Map<String, Long> body) {
        guardrailPolicyService.updateConversationPolicy(id, body.get("policyId"));
        return ApiResult.success(conversationService.getDTO(id));
    }

    @PutMapping("/{id}/pin")
    public ApiResult<ConversationDTO> pin(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        return ApiResult.success(conversationService.pin(id, Boolean.TRUE.equals(body.get("pinned"))));
    }

    @PutMapping("/{id}/project")
    public ApiResult<ConversationDTO> moveToProject(@PathVariable Long id, @RequestBody Map<String, Long> body) {
        return ApiResult.success(conversationService.moveToProject(id, body.get("projectId")));
    }

    @PostMapping("/{id}/approvals/{toolCallId}")
    public ApiResult<?> decideApproval(@PathVariable Long id, @PathVariable String toolCallId,
                                       @RequestBody Map<String, String> body) {
        boolean approved = "approve".equals(body.get("decision"));
        return chatService.decideApproval(id, toolCallId, approved)
                ? ApiResult.success() : ApiResult.failure("审批不存在或已处理");
    }

    @GetMapping("/{id}/title-wait")
    public DeferredResult<ApiResult<Map<String, String>>> waitTitle(@PathVariable Long id) {
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
