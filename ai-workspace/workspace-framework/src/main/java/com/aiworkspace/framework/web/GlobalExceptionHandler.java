package com.aiworkspace.framework.web;

import com.aiworkspace.common.exception.BusinessException;
import com.aiworkspace.common.response.Result;
import com.aiworkspace.common.response.ResultCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 全局异常处理器
 *
 * 通过 @RestControllerAdvice 统一拦截控制器抛出的异常，转换为标准 Result 响应，
 * 避免异常堆栈直接暴露给前端，保证 API 错误格式一致。
 *
 * 主要职责：
 * 1. 业务异常 → 透传业务码与提示信息
 * 2. 参数校验异常 → 聚合字段错误信息，返回 400
 * 3. 鉴权/授权异常 → 返回 401/403
 * 4. 兜底未知异常 → 记录堆栈并返回 500，不泄露内部细节
 *
 * @author
 * @since 2026
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理业务异常
     *
     * 业务异常属于可预期的流程性错误，按 warn 级别记录（无需完整堆栈），
     * 并将异常中携带的业务码与提示原样返回前端。
     *
     * @param e 业务异常
     * @return 携带业务码与提示信息的失败响应
     */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        log.warn("业务异常: {}", e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    /**
     * 处理 @RequestBody 参数校验失败（@Valid 触发）
     *
     * @param e 方法参数校验异常
     * @return 聚合各字段错误提示的 400 响应
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidationException(MethodArgumentNotValidException e) {
        // 将所有字段校验失败信息拼接为单条可读提示返回前端
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        return Result.fail(ResultCode.BAD_REQUEST.getCode(), message);
    }

    /**
     * 处理表单/参数绑定校验失败
     *
     * @param e 绑定异常
     * @return 聚合各字段错误提示的 400 响应
     */
    @ExceptionHandler(BindException.class)
    public Result<Void> handleBindException(BindException e) {
        String message = e.getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        return Result.fail(ResultCode.BAD_REQUEST.getCode(), message);
    }

    /**
     * 处理认证失败异常（未登录或令牌无效）
     *
     * @param e 认证异常
     * @return 401 未授权响应
     */
    @ExceptionHandler(AuthenticationException.class)
    public Result<Void> handleAuthenticationException(AuthenticationException e) {
        return Result.fail(ResultCode.UNAUTHORIZED);
    }

    /**
     * 处理授权失败异常（已登录但权限不足，RBAC 拦截）
     *
     * @param e 访问拒绝异常
     * @return 403 权限不足响应
     */
    @ExceptionHandler(AccessDeniedException.class)
    public Result<Void> handleAccessDeniedException(AccessDeniedException e) {
        return Result.fail(ResultCode.FORBIDDEN);
    }

    /**
     * 兜底异常处理
     *
     * 捕获所有未被上述处理器命中的异常，按 error 级别记录完整堆栈以便排查，
     * 但对外只返回通用 500 提示，避免泄露内部实现细节。
     *
     * @param e 未知异常
     * @return 500 系统异常响应
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return Result.fail(ResultCode.INTERNAL_ERROR);
    }
}
