package com.gacfox.meowclaw.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ApiResult<T> implements Serializable {

    private String code;
    private String message;
    private T data;

    public static ApiResult<?> success() {
        return new ApiResult<>("0", "success", null);
    }

    public static <T> ApiResult<T> success(T data) {
        return new ApiResult<>("0", "success", data);
    }

    public static ApiResult<?> success(String message) {
        return new ApiResult<>("0", message, null);
    }

    public static <T> ApiResult<T> success(String message, T data) {
        return new ApiResult<>("0", message, data);
    }

    public static <T> ApiResult<T> failure() {
        return new ApiResult<>("1", "failure", null);
    }

    public static <T> ApiResult<T> failure(String message) {
        return new ApiResult<>("1", message, null);
    }

    public static <T> ApiResult<T> failure(String code, String message) {
        return new ApiResult<>(code, message, null);
    }
}
