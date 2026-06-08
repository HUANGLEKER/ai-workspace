package com.aiworkspace.common.constant;

/**
 * 全局通用常量
 *
 * 集中定义跨模块共享的鉴权与状态常量，避免魔法值散落各处。
 *
 * @author
 * @since 2026
 */
public class CommonConstants {

    /** 鉴权请求头名称 */
    public static final String TOKEN_HEADER = "Authorization";

    /** JWT 令牌前缀（注意末尾含空格），解析时需剥离 */
    public static final String TOKEN_PREFIX = "Bearer ";

    /** Redis 中登录令牌缓存的 key 前缀 */
    public static final String REDIS_TOKEN_PREFIX = "login:token:";

    /** 账号/资源状态：正常（启用） */
    public static final Integer STATUS_NORMAL = 1;

    /** 账号/资源状态：禁用 */
    public static final Integer STATUS_DISABLED = 0;
}
