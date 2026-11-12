package com.gacfox.meowclaw.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class TraceObservationService {
    public static final String CONFIG_KEY = "trace.enabled";

    private final SystemConfigService systemConfigService;

    @Autowired
    public TraceObservationService(SystemConfigService systemConfigService) {
        this.systemConfigService = systemConfigService;
    }

    /**
     * 是否记录新的调用追踪明细，未配置或配置值异常时默认开启
     */
    public boolean isEnabled() {
        String value = systemConfigService.get(CONFIG_KEY, Boolean.TRUE.toString());
        return !Boolean.FALSE.toString().equalsIgnoreCase(value);
    }

    public boolean updateEnabled(boolean enabled) {
        systemConfigService.set(CONFIG_KEY, Boolean.toString(enabled));
        return enabled;
    }
}
