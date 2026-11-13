package com.gacfox.meowclaw.service;

import com.gacfox.meowclaw.dto.SystemResourceMetricsDTO;
import com.sun.management.OperatingSystemMXBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;

@Service
public class SystemResourceService {
    public static final String CONFIG_KEY = "system-resource-monitor.enabled";

    private final SystemConfigService systemConfigService;
    private final OperatingSystemMXBean operatingSystemMXBean;

    @Autowired
    public SystemResourceService(SystemConfigService systemConfigService) {
        this(systemConfigService, operatingSystemMXBean());
    }

    SystemResourceService(SystemConfigService systemConfigService, OperatingSystemMXBean operatingSystemMXBean) {
        this.systemConfigService = systemConfigService;
        this.operatingSystemMXBean = operatingSystemMXBean;
    }

    /**
     * 是否在前端展示系统资源监控，未配置或配置值异常时默认开启
     */
    public boolean isEnabled() {
        String value = systemConfigService.get(CONFIG_KEY, Boolean.TRUE.toString());
        return !Boolean.FALSE.toString().equalsIgnoreCase(value);
    }

    public boolean updateEnabled(boolean enabled) {
        systemConfigService.set(CONFIG_KEY, Boolean.toString(enabled));
        return enabled;
    }

    /**
     * 按需读取整个操作系统的资源占用，不启动后台采样任务
     */
    public SystemResourceMetricsDTO getMetrics() {
        Double cpuUsage = null;
        Double memoryUsage = null;
        if (operatingSystemMXBean != null) {
            try {
                cpuUsage = cpuPercentage(operatingSystemMXBean.getCpuLoad());
                long totalMemory = operatingSystemMXBean.getTotalMemorySize();
                long freeMemory = operatingSystemMXBean.getFreeMemorySize();
                if (totalMemory > 0 && freeMemory >= 0) {
                    long usedMemory = Math.max(totalMemory - freeMemory, 0);
                    memoryUsage = Math.min(usedMemory * 100.0 / totalMemory, 100.0);
                }
            } catch (RuntimeException | LinkageError ignored) {
                // 部分 JVM 或运行环境可能不支持系统指标，此时返回不可用状态
            }
        }
        return new SystemResourceMetricsDTO(cpuUsage, memoryUsage, System.currentTimeMillis());
    }

    private static Double cpuPercentage(double cpuLoad) {
        if (!Double.isFinite(cpuLoad) || cpuLoad < 0) {
            return null;
        }
        return Math.min(cpuLoad, 1.0) * 100.0;
    }

    private static OperatingSystemMXBean operatingSystemMXBean() {
        try {
            if (ManagementFactory.getOperatingSystemMXBean() instanceof OperatingSystemMXBean bean) {
                return bean;
            }
        } catch (RuntimeException | LinkageError ignored) {
            // 不支持系统指标时保持服务可用
        }
        return null;
    }
}
