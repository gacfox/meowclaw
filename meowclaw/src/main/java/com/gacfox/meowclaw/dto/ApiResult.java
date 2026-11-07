package com.gacfox.meowclaw.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "通用接口响应")
public class ApiResult<T> implements Serializable {
    @Schema(description = "响应状态码")
    private String code;
    @Schema(description = "响应消息")
    private String message;
    @Schema(description = "响应数据")
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
