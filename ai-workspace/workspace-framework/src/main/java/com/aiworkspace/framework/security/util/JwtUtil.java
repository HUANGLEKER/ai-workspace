package com.aiworkspace.framework.security.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

/**
 * JWT 工具类
 *
 * 基于 JJWT 实现无状态认证令牌的签发与校验，签名算法为 HS256（HMAC-SHA256）。
 *
 * 主要职责：
 * 1. 根据用户名签发带过期时间的 JWT
 * 2. 从令牌中解析用户名
 * 3. 校验令牌签名与有效期
 *
 * 设计说明：密钥与过期时间从配置中注入，便于在不同环境间切换；
 * 采用对称密钥（HMAC），同一密钥既用于签名也用于验签。
 *
 * @author
 * @since 2026
 */
@Component
public class JwtUtil {

    // JWT 签名密钥（Base64 编码），用于 HS256 对称签名，必须保密
    @Value("${jwt.secret}")
    private String secret;

    // 令牌有效期（毫秒），从配置注入以便按环境调整
    @Value("${jwt.expiration}")
    private long expiration;

    /**
     * 签发 JWT 令牌
     *
     * @param username 用户名，作为令牌主体（subject）
     * @return 已签名的 JWT 字符串
     */
    public String generateToken(String username) {
        return Jwts.builder()
                .subject(username)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getKey())
                .compact();
    }

    /**
     * 从令牌中解析用户名
     *
     * @param token JWT 字符串
     * @return 令牌主体中存储的用户名
     */
    public String extractUsername(String token) {
        return extractClaims(token).getSubject();
    }

    /**
     * 校验令牌是否有效（签名正确且未过期）
     *
     * @param token JWT 字符串
     * @return true 表示有效，false 表示签名非法或已过期
     */
    public boolean isTokenValid(String token) {
        try {
            return !extractClaims(token).getExpiration().before(new Date());
        } catch (Exception e) {
            // 解析异常（签名不匹配、格式非法、已过期等）一律视为无效，避免向上抛出影响过滤器链
            return false;
        }
    }

    // 验签并解析令牌载荷；verifyWith 会校验签名，签名不匹配将抛出异常
    private Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    // 将 Base64 密钥解码为 HMAC-SHA 密钥；密钥长度需满足 HS256 的最小位数要求
    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    }
}
