package com.aiworkspace.common.exception;

import com.aiworkspace.common.response.ResultCode;

/**
 * 业务异常
 *
 * 表示可预期的业务规则错误（如资源不存在、归属校验失败、参数非法等），
 * 由全局异常处理器统一捕获并转换为标准 Result 响应。
 *
 * 设计说明：继承 RuntimeException 为非受检异常，无需在方法签名声明，
 * 便于在 Service 层任意位置中断流程；携带业务码以便前端区分处理。
 *
 * @author
 * @since 2026
 */
public class BusinessException extends RuntimeException {

    /** 业务状态码 */
    private final int code;

    public int getCode() {
        return code;
    }

    /**
     * 以自定义提示构造异常，业务码默认为系统异常码（500）
     *
     * @param message 错误提示
     */
    public BusinessException(String message) {
        super(message);
        this.code = ResultCode.INTERNAL_ERROR.getCode();
    }

    /**
     * 基于预定义状态码构造异常，复用其码值与默认文案
     *
     * @param resultCode 响应状态码枚举
     */
    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    /**
     * 以自定义业务码与提示构造异常
     *
     * @param code    业务状态码
     * @param message 错误提示
     */
    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }
}
