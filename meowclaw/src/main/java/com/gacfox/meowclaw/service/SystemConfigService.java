package com.gacfox.meowclaw.service;

import com.gacfox.meowclaw.entity.SysConfig;
import com.gacfox.meowclaw.repository.SysConfigRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SystemConfigService {
    private final SysConfigRepository sysConfigRepository;

    @Autowired
    public SystemConfigService(SysConfigRepository sysConfigRepository) {
        this.sysConfigRepository = sysConfigRepository;
    }

    @Transactional(readOnly = true)
    public String get(String key, String defaultValue) {
        return sysConfigRepository.findByConfigKey(key)
                .map(SysConfig::getConfigValue)
                .orElse(defaultValue);
    }

    @Transactional
    public void set(String key, String value) {
        SysConfig config = sysConfigRepository.findByConfigKey(key).orElseGet(SysConfig::new);
        long now = System.currentTimeMillis();
        if (config.getId() == null) {
            config.setConfigKey(key);
            config.setCreatedAt(now);
        }
        config.setConfigValue(value);
        config.setUpdatedAt(now);
        sysConfigRepository.save(config);
    }
}
