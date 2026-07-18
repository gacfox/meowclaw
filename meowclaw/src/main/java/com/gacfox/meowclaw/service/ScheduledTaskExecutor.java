package com.gacfox.meowclaw.service;

import com.gacfox.meowclaw.entity.ScheduledTask;
import com.gacfox.meowclaw.entity.ScheduledTaskExecution;
import com.gacfox.meowclaw.repository.ScheduledTaskExecutionRepository;
import com.gacfox.meowclaw.repository.ScheduledTaskRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ScheduledTaskExecutor {
    private final ChatService chatService;
    private final ConversationService conversationService;
    private final ScheduledTaskRepository scheduledTaskRepository;
    private final ScheduledTaskExecutionRepository scheduledTaskExecutionRepository;

    public ScheduledTaskExecutor(ChatService chatService,
                                 ConversationService conversationService,
                                 ScheduledTaskRepository scheduledTaskRepository,
                                 ScheduledTaskExecutionRepository scheduledTaskExecutionRepository) {
        this.chatService = chatService;
        this.conversationService = conversationService;
        this.scheduledTaskRepository = scheduledTaskRepository;
        this.scheduledTaskExecutionRepository = scheduledTaskExecutionRepository;
    }

    public void execute(Long taskId) {
        ScheduledTask task = scheduledTaskRepository.findById(taskId).orElse(null);
        if (task == null) {
            log.warn("Scheduled task {} not found", taskId);
            return;
        }

        long now = System.currentTimeMillis();

        // Determine conversation
        Long conversationId;
        if (Boolean.TRUE.equals(task.getCreateNewSession())) {
            conversationId = conversationService.create(task.getAgentId(), "SCHEDULED").getId();
        } else {
            conversationId = scheduledTaskExecutionRepository
                    .findTopByScheduledTaskIdOrderByExecutedAtDesc(taskId)
                    .map(ScheduledTaskExecution::getConversationId)
                    .filter(conversationService::existsById)
                    .orElseGet(() -> conversationService.create(task.getAgentId(), "SCHEDULED").getId());
        }

        // Create execution record
        ScheduledTaskExecution execution = new ScheduledTaskExecution();
        execution.setScheduledTaskId(taskId);
        execution.setConversationId(conversationId);
        execution.setStatus("RUNNING");
        execution.setExecutedAt(now);
        execution = scheduledTaskExecutionRepository.save(execution);

        // Update task last status
        task.setLastStatus("RUNNING");
        task.setLastExecutedAt(now);
        task.setUpdatedAt(now);
        scheduledTaskRepository.save(task);

        // Execute chat synchronously
        try {
            chatService.chat(conversationId, task.getUserPrompt()).collectList().block();
            execution.setStatus("SUCCESS");
        } catch (Exception e) {
            log.error("Scheduled task {} execution failed", taskId, e);
            execution.setStatus("ERROR");
        }

        scheduledTaskExecutionRepository.save(execution);

        task.setLastStatus(execution.getStatus());
        task.setUpdatedAt(System.currentTimeMillis());
        scheduledTaskRepository.save(task);
    }
}
