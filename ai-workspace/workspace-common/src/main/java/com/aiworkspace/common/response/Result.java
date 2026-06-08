package com.aiworkspace.common.response;

import lombok.Data;

/**
 * 统一响应结果包装
 *
 * 所有 REST 接口的标准返回结构，由 code/message/data 三段组成，
 * 提供成功与失败的静态工厂方法，统一前后端数据契约。
 *
 * @param <T> 业务数据类型
 * @author
 * @since 2026
 */
@Data
public class Result<T> {

    /** 业务状态码（见 ResultCode） */
    private Integer code;
    /** 提示信息 */
    private String message;
    /** 业务数据载荷 */
    private T data;

    public Result() {}

    public Result(Integer code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /**
     * 构造无数据的成功响应
     *
     * @return 成功结果
     */
    public static <T> Result<T> ok() {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), null);
    }

    /**
     * 构造携带数据的成功响应
     *
     * @param data 业务数据
     * @return 成功结果
     */
    public static <T> Result<T> ok(T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data);
    }

    /**
     * 构造失败响应（默认系统异常码 500，自定义提示）
     *
     * @param message 提示信息
     * @return 失败结果
     */
    public static <T> Result<T> fail(String message) {
        return new Result<>(ResultCode.INTERNAL_ERROR.getCode(), message, null);
    }

    /**
     * 构造失败响应（自定义业务码与提示）
     *
     * @param code    业务状态码
     * @param message 提示信息
     * @return 失败结果
     */
    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null);
    }

    /**
     * 基于预定义状态码构造失败响应
     *
     * @param resultCode 响应状态码枚举
     * @return 失败结果
     */
    public static <T> Result<T> fail(ResultCode resultCode) {
        return new Result<>(resultCode.getCode(), resultCode.getMessage(), null);
    }
}
