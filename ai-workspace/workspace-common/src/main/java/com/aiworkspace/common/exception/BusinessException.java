package com.aiworkspace.common.exception;

import com.aiworkspace.common.response.ResultCode;

public class BusinessException extends RuntimeException {

    private final int code;

    public int getCode() {
        return code;
    }

    public BusinessException(String message) {
        super(message);
        this.code = ResultCode.INTERNAL_ERROR.getCode();
    }

    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }
}
