package com.gacfox.meowclaw.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Json序列化和反序列化工具类
 */
@Component
public class JsonUtil {
    private final ObjectMapper objectMapper;

    public JsonUtil(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 将对象序列化为JSON
     *
     * @param o 对象
     * @return JSON字符串
     */
    public String dump(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 反序列化JSON字符串为对象
     *
     * @param jsonStr JSON字符串
     * @return 结果Map
     */
    public Map<String, Object> load(String jsonStr) {
        try {
            return objectMapper.readValue(jsonStr, new TypeReference<Map<String, Object>>() {
            });
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 反序列化JSON字符串为对象
     *
     * @param jsonStr JSON字符串
     * @param clazz   类型信息
     * @param <T>     结果类型
     * @return 结果对象
     */
    public <T> T load(String jsonStr, Class<T> clazz) {
        try {
            return objectMapper.readValue(jsonStr, clazz);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 反序列化JSON字符串为对象
     *
     * @param jsonStr       JSON字符串
     * @param typeReference 类型信息
     * @param <T>           结果类型
     * @return 结果对象
     */
    public <T> T load(String jsonStr, TypeReference<T> typeReference) {
        try {
            return objectMapper.readValue(jsonStr, typeReference);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
