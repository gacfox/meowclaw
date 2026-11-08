package com.gacfox.meowclaw.service;

import com.gacfox.meowclaw.dto.TraceRetentionPolicy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Service
public class TraceRetentionService {
    public static final String CONFIG_KEY = "trace.retention-policy";

    private final SystemConfigService systemConfigService;
    private final TraceService traceService;

    @Autowired
    public TraceRetentionService(SystemConfigService systemConfigService, TraceService traceService) {
        this.systemConfigService = systemConfigService;
        this.traceService = traceService;
    }

    public TraceRetentionPolicy getPolicy() {
        String value = systemConfigService.get(CONFIG_KEY, TraceRetentionPolicy.ALL.name());
        try {
            return TraceRetentionPolicy.valueOf(value);
        } catch (IllegalArgumentException e) {
            return TraceRetentionPolicy.ALL;
        }
    }

    public TraceRetentionPolicy updatePolicy(TraceRetentionPolicy policy) {
        systemConfigService.set(CONFIG_KEY, policy.name());
        return policy;
    }

    /**
     * 清理截止当前策略之前的追踪明细；全部留存时不执行删除
     *
     * @return 删除的调用追踪明细数量
     */
    public int cleanupExpired() {
        TraceRetentionPolicy policy = getPolicy();
        if (policy == TraceRetentionPolicy.ALL) {
            return 0;
        }
        return traceService.deleteExpired(cutoff(policy, System.currentTimeMillis(), ZoneId.systemDefault()));
    }

    long cutoff(TraceRetentionPolicy policy, long now, ZoneId zone) {
        ZonedDateTime current = Instant.ofEpochMilli(now).atZone(zone);
        ZonedDateTime cutoff = switch (policy) {
            case DAYS_3 -> current.minusDays(3);
            case DAYS_7 -> current.minusDays(7);
            case MONTH_1 -> current.minusMonths(1);
            case YEAR_1 -> current.minusYears(1);
            case ALL -> current;
        };
        return cutoff.toInstant().toEpochMilli();
    }
}
