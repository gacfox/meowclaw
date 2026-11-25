package com.gacfox.meowclaw.exception;

import com.gacfox.meowclaw.dto.ApiResult;
import com.gacfox.meowclaw.util.ClientDisconnectUtil;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResult<?> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ApiResult.failure(message);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResult<?> handleIllegalArgument(IllegalArgumentException e) {
        return ApiResult.failure(e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResult<?> handleIllegalState(IllegalStateException e) {
        return ApiResult.failure(e.getMessage());
    }

    @ExceptionHandler(ForbiddenException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiResult<?> handleForbidden(ForbiddenException e) {
        return ApiResult.failure("403", "无权限");
    }

    @ExceptionHandler(UnsupportedWorkspaceFileTypeException.class)
    @ResponseStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
    public ApiResult<?> handleUnsupportedWorkspaceFileType(UnsupportedWorkspaceFileTypeException e) {
        return ApiResult.failure(e.getMessage());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResult<?> handleNotFound(NoResourceFoundException e) {
        return ApiResult.failure("资源不存在");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResult<?> handleException(Exception e, HttpServletResponse response) {
        if (ClientDisconnectUtil.isClientDisconnect(e)) {
            log.info("客户端取消响应输出");
            return null;
        }
        String contentType = response.getContentType();
        if (response.isCommitted() || (contentType != null && !contentType.toLowerCase().contains("json"))) {
            log.warn("当前响应无法追加 JSON 错误（Content-Type: {}）", contentType, e);
            return null;
        }
        log.error("Unhandled exception", e);
        return ApiResult.failure("服务器内部错误");
    }
}
