package com.gacfox.meowclaw.service;

import com.gacfox.meowclaw.converter.ScheduledTaskConverter;
import com.gacfox.meowclaw.converter.ScheduledTaskExecutionConverter;
import com.gacfox.meowclaw.dto.CreateScheduledTaskRequest;
import com.gacfox.meowclaw.dto.ScheduledTaskDTO;
import com.gacfox.meowclaw.entity.ScheduledTask;
import com.gacfox.meowclaw.repository.ScheduledTaskExecutionRepository;
import com.gacfox.meowclaw.repository.ScheduledTaskRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ScheduledTaskServiceTest {
    private final ScheduledTaskRepository taskRepository = mock(ScheduledTaskRepository.class);
    private final SchedulerManager schedulerManager = mock(SchedulerManager.class);
    private final ScheduledTaskExecutor taskExecutor = mock(ScheduledTaskExecutor.class);
    private final ScheduledTaskConverter converter = mock(ScheduledTaskConverter.class);
    private final ScheduledTaskService service = new ScheduledTaskService(taskRepository,
            mock(ScheduledTaskExecutionRepository.class), converter,
            mock(ScheduledTaskExecutionConverter.class), schedulerManager, taskExecutor);

    @Test
    void createImportedPersistsDisabledTaskWithoutScheduling() {
        when(taskRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(converter.toDTO(any())).thenReturn(new ScheduledTaskDTO());
        CreateScheduledTaskRequest request = new CreateScheduledTaskRequest("导入任务", 8L,
                "执行任务", "0 0 9 * * *", false);

        service.createImported(request);

        ArgumentCaptor<ScheduledTask> captor = ArgumentCaptor.forClass(ScheduledTask.class);
        verify(taskRepository).save(captor.capture());
        assertThat(captor.getValue().getEnabled()).isFalse();
        assertThat(captor.getValue().getAgentId()).isEqualTo(8L);
        assertThat(captor.getValue().getLastStatus()).isNull();
        assertThat(captor.getValue().getLastExecutedAt()).isNull();
        verifyNoInteractions(schedulerManager, taskExecutor);
    }
}
