package com.aiworkspace.system.dto;

import lombok.Data;

/**
 * 登录响应 DTO
 *
 * 承载登录成功后签发的 JWT 令牌，前端持有后在后续请求头携带。
 */
@Data
public class LoginResponse {

    /** JWT 访问令牌 */
    private String token;

    public LoginResponse(String token) {
        this.token = token;
    }
}
