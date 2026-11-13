package com.gacfox.meowclaw.service;

import com.gacfox.meowclaw.dto.SystemResourceMetricsDTO;
import com.sun.management.OperatingSystemMXBean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SystemResourceServiceTest {
    private SystemConfigService systemConfigService;
    private OperatingSystemMXBean operatingSystemMXBean;
    private SystemResourceService service;

    @BeforeEach
    void setUp() {
        systemConfigService = mock(SystemConfigService.class);
        operatingSystemMXBean = mock(OperatingSystemMXBean.class);
        service = new SystemResourceService(systemConfigService, operatingSystemMXBean);
    }

    @Test
    void monitorIsEnabledUnlessExplicitlyDisabled() {
        when(systemConfigService.get(SystemResourceService.CONFIG_KEY, "true")).thenReturn("unexpected");
        assertThat(service.isEnabled()).isTrue();

        when(systemConfigService.get(SystemResourceService.CONFIG_KEY, "true")).thenReturn("FALSE");
        assertThat(service.isEnabled()).isFalse();
    }

    @Test
    void updateEnabledPersistsConfiguration() {
        assertThat(service.updateEnabled(false)).isFalse();
        verify(systemConfigService).set(SystemResourceService.CONFIG_KEY, "false");
    }

    @Test
    void metricsUseWholeSystemCpuAndMemory() {
        when(operatingSystemMXBean.getCpuLoad()).thenReturn(0.255);
        when(operatingSystemMXBean.getTotalMemorySize()).thenReturn(16_000L);
        when(operatingSystemMXBean.getFreeMemorySize()).thenReturn(4_000L);

        SystemResourceMetricsDTO metrics = service.getMetrics();

        assertThat(metrics.getCpuUsage()).isEqualTo(25.5);
        assertThat(metrics.getMemoryUsage()).isEqualTo(75.0);
        assertThat(metrics.getTimestamp()).isPositive();
    }

    @Test
    void unsupportedMetricsAreReturnedAsNull() {
        when(operatingSystemMXBean.getCpuLoad()).thenReturn(Double.NaN);
        when(operatingSystemMXBean.getTotalMemorySize()).thenReturn(0L);

        SystemResourceMetricsDTO metrics = service.getMetrics();

        assertThat(metrics.getCpuUsage()).isNull();
        assertThat(metrics.getMemoryUsage()).isNull();
    }
}
