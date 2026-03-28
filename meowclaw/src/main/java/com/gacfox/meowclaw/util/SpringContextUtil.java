package com.gacfox.meowclaw.util;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

@Component
public class SpringContextUtil implements ApplicationContextAware {
    private static ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext context) throws BeansException {
        applicationContext = context;
    }

    public static <T> T getBean(Class<T> clazz) {
        ApplicationContext context = applicationContext;
        if (context == null) {
            throw new IllegalStateException("Spring ApplicationContext尚未初始化");
        }
        return context.getBean(clazz);
    }
}
