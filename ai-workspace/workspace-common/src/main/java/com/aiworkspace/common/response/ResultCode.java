package com.aiworkspace.common.response;

/**
 * 统一响应状态码枚举
 *
 * 定义 API 标准业务码与默认提示文案，与 HTTP 语义对齐，
 * 供 Result 与全局异常处理器统一引用，保证前后端约定一致。
 *
 * @author
 * @since 2026
 */
public enum ResultCode {

    /** 操作成功 */
    SUCCESS(200, "操作成功"),
    /** 请求参数错误（参数校验失败等） */
    BAD_REQUEST(400, "请求参数错误"),
    /** 未认证：未登录或令牌无效 */
    UNAUTHORIZED(401, "未授权，请先登录"),
    /** 已认证但权限不足（RBAC 拦截） */
    FORBIDDEN(403, "权限不足"),
    /** 资源不存在 */
    NOT_FOUND(404, "资源不存在"),
    /** 系统内部异常（兜底） */
    INTERNAL_ERROR(500, "系统异常");

    /** 业务状态码 */
    private final int code;
    /** 默认提示文案 */
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
