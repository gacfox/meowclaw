package com.gacfox.meowclaw.service;

import com.cronutils.model.Cron;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;
import com.gacfox.meowclaw.dto.PageDto;
import com.gacfox.meowclaw.dto.ScheduledTaskDto;
import com.gacfox.meowclaw.entity.AgentConfig;
import com.gacfox.meowclaw.entity.Conversation;
import com.gacfox.meowclaw.entity.ScheduledTask;
import com.gacfox.meowclaw.exception.ServiceNotSatisfiedException;
import com.gacfox.meowclaw.repository.AgentConfigRepository;
import com.gacfox.meowclaw.repository.ConversationRepository;
import com.gacfox.meowclaw.repository.ScheduledTaskRepository;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ScheduledTaskService {
    private final ScheduledTaskRepository scheduledTaskRepository;
    private final AgentConfigRepository agentConfigRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationService conversationService;
    private final TaskSchedulerService taskSchedulerService;
    private final CronParser cronParser;

    public ScheduledTaskService(ScheduledTaskRepository scheduledTaskRepository,
                                AgentConfigRepository agentConfigRepository,
                                ConversationRepository conversationRepository,
                                ConversationService conversationService,
                                TaskSchedulerService taskSchedulerService) {
        this.scheduledTaskRepository = scheduledTaskRepository;
        this.agentConfigRepository = agentConfigRepository;
        this.conversationRepository = conversationRepository;
        this.conversationService = conversationService;
        this.taskSchedulerService = taskSchedulerService;
        this.cronParser = new CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.QUARTZ));
    }

    public PageDto<ScheduledTaskDto> findAll(int page, int pageSize) {
        List<ScheduledTask> tasks = scheduledTaskRepository.findAll();
        List<ScheduledTaskDto> items = tasks.stream().map(this::toDto).collect(Collectors.toList());
        return PageDto.of(items, tasks.size(), 1, tasks.size());
    }

    public ScheduledTaskDto findById(Long id) {
        ScheduledTask task = scheduledTaskRepository.findById(id)
                .orElseThrow(() -> new ServiceNotSatisfiedException("定时任务不存在"));
        return toDto(task);
    }

    public ScheduledTaskDto create(ScheduledTaskDto dto) {
        validateAgentConfig(dto.getAgentConfigId());
        validateCronExpression(dto.getCronExpression());

        ScheduledTask task = new ScheduledTask();
        BeanUtils.copyProperties(dto, task);

        Instant now = Instant.now();
        task.setCreatedAtInstant(now);
        task.setUpdatedAtInstant(now);

        if (!task.isNewSessionEach()) {
            Conversation conversation = conversationService.createScheduledConversation(dto.getAgentConfigId());
            task.setBoundConversationId(conversation.getId());
        }

        ScheduledTask saved = scheduledTaskRepository.save(task);
        taskSchedulerService.scheduleOrReschedule(saved);
        return toDto(saved);
    }

    public ScheduledTaskDto update(Long id, ScheduledTaskDto dto) {
        ScheduledTask task = scheduledTaskRepository.findById(id)
                .orElseThrow(() -> new ServiceNotSatisfiedException("定时任务不存在"));

        validateAgentConfig(dto.getAgentConfigId());
        validateCronExpression(dto.getCronExpression());

        boolean wasNewSessionEach = task.isNewSessionEach();
        boolean isNewSessionEach = dto.isNewSessionEach();
        Long previousAgentConfigId = task.getAgentConfigId();

        task.setName(dto.getName());
        task.setAgentConfigId(dto.getAgentConfigId());
        task.setUserPrompt(dto.getUserPrompt());
        task.setCronExpression(dto.getCronExpression());
        task.setNewSessionEach(isNewSessionEach);
        task.setEnabled(dto.isEnabled());
        task.setUpdatedAtInstant(Instant.now());

        if (!wasNewSessionEach && isNewSessionEach) {
            if (task.getBoundConversationId() != null) {
                task.setBoundConversationId(null);
            }
        } else if (wasNewSessionEach && !isNewSessionEach) {
            Conversation conversation = conversationService.createScheduledConversation(dto.getAgentConfigId());
            task.setBoundConversationId(conversation.getId());
        } else if (!isNewSessionEach) {
            updateBoundConversationAgent(task, previousAgentConfigId);
        }

        ScheduledTask saved = scheduledTaskRepository.save(task);
        taskSchedulerService.scheduleOrReschedule(saved);
        return toDto(saved);
    }

    public void delete(Long id) {
        ScheduledTask task = scheduledTaskRepository.findById(id)
                .orElseThrow(() -> new ServiceNotSatisfiedException("定时任务不存在"));

        taskSchedulerService.cancelTask(id);

        if (!task.isNewSessionEach() && task.getBoundConversationId() != null) {
            conversationRepository.deleteById(task.getBoundConversationId());
        }

        scheduledTaskRepository.deleteById(id);
    }

    public ScheduledTaskDto toggleEnabled(Long id) {
        ScheduledTask task = scheduledTaskRepository.findById(id)
                .orElseThrow(() -> new ServiceNotSatisfiedException("定时任务不存在"));

        task.setEnabled(!task.isEnabled());
        task.setUpdatedAtInstant(Instant.now());
        ScheduledTask saved = scheduledTaskRepository.save(task);
        taskSchedulerService.scheduleOrReschedule(saved);
        return toDto(saved);
    }

    public void trigger(Long id) {
        ScheduledTask task = scheduledTaskRepository.findById(id)
                .orElseThrow(() -> new ServiceNotSatisfiedException("定时任务不存在"));
        taskSchedulerService.triggerTask(task);
    }

    public String getNextExecutionTime(String cronExpression) {
        try {
            Cron cron = cronParser.parse(cronExpression);
            ExecutionTime executionTime = ExecutionTime.forCron(cron);
            Optional<ZonedDateTime> next = executionTime.nextExecution(ZonedDateTime.now());
            return next.map(zdt -> zdt.toString()).orElse("无法计算");
        } catch (Exception e) {
            return "表达式无效";
        }
    }

    private void validateAgentConfig(Long agentConfigId) {
        agentConfigRepository.findById(agentConfigId)
                .orElseThrow(() -> new ServiceNotSatisfiedException("智能体配置不存在"));
    }

    private void validateCronExpression(String cronExpression) {
        try {
            cronParser.parse(cronExpression);
        } catch (Exception e) {
            throw new ServiceNotSatisfiedException("Cron表达式格式无效: " + e.getMessage());
        }
    }

    private ScheduledTaskDto toDto(ScheduledTask task) {
        ScheduledTaskDto dto = new ScheduledTaskDto();
        BeanUtils.copyProperties(task, dto);

        Optional<AgentConfig> agentOpt = agentConfigRepository.findById(task.getAgentConfigId());
        agentOpt.ifPresent(agent -> dto.setAgentName(agent.getName()));

        return dto;
    }

    private void updateBoundConversationAgent(ScheduledTask task, Long previousAgentConfigId) {
        if (task.getBoundConversationId() == null) {
            return;
        }
        if (previousAgentConfigId != null && previousAgentConfigId.equals(task.getAgentConfigId())) {
            return;
        }
        Optional<Conversation> conversationOpt = conversationRepository.findById(task.getBoundConversationId());
        if (conversationOpt.isEmpty()) {
            return;
        }
        Conversation conversation = conversationOpt.get();
        conversation.setAgentConfigId(task.getAgentConfigId());
        conversation.setUpdatedAtInstant(Instant.now());
        Conversation ignored = conversationRepository.save(conversation);
    }
}
