package com.gacfox.meowclaw.exception;

import com.gacfox.meowclaw.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(ServiceNotSatisfiedException.class)
    public ApiResponse<Void> handleServiceNotSatisfiedException(ServiceNotSatisfiedException e) {
        return ApiResponse.error(400, e.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    public ApiResponse<Void> handleRuntimeException(RuntimeException e) {
        log.error("运行时异常", e);
        return ApiResponse.error(500, "服务器内部错误");
    }

    @ExceptionHandler(BindException.class)
    public ApiResponse<Void> handleBindException(BindException e) {
        FieldError fieldError = e.getFieldError();
        String message = fieldError != null ? fieldError.getDefaultMessage() : "参数校验失败";
        return ApiResponse.error(400, message);
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return ApiResponse.error(500, "服务器内部错误");
    }
}
