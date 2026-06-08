package com.aiworkspace.system.security;

import com.aiworkspace.system.entity.SysUser;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * 登录用户主体（Spring Security Principal）
 *
 * 实现 {@link UserDetails}，封装认证后的用户实体与其 RBAC 角色码，
 * 贯穿整个安全上下文，供 {@code @PreAuthorize} 与业务侧获取当前用户/归属判定使用。
 *
 * 设计说明：角色码（role_code）来自 {@code sys_user_role → sys_role}，
 * 已带 {@code ROLE_} 前缀，可直接被 {@code hasRole('ADMIN')} 匹配。
 *
 * @since 2026
 */
public class LoginUser implements UserDetails {

    private final SysUser sysUser;
    private final List<String> roles;

    public LoginUser(SysUser sysUser) {
        this(sysUser, List.of());
    }

    public LoginUser(SysUser sysUser, List<String> roles) {
        this.sysUser = sysUser;
        this.roles = roles != null ? roles : List.of();
    }

    public SysUser getSysUser() {
        return sysUser;
    }

    public List<String> getRoles() {
        return roles;
    }

    /**
     * 返回授予的权限集合
     *
     * @return 由角色码映射而来的 GrantedAuthority 列表
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // role_code 已带 ROLE_ 前缀（如 ROLE_ADMIN），可直接与 hasRole('ADMIN') 匹配，无需再拼接
        return roles.stream()
                .map(SimpleGrantedAuthority::new)
                .map(GrantedAuthority.class::cast)
                .toList();
    }

    @Override
    public String getPassword() {
        return sysUser.getPassword();
    }

    @Override
    public String getUsername() {
        return sysUser.getUsername();
    }

    /**
     * 账号是否启用
     *
     * @return 仅当用户状态为 1（正常）时返回 true；禁用账号将被拒绝登录
     */
    @Override
    public boolean isEnabled() {
        // 安全：状态非 1（禁用/异常）的账号视为不可用，Spring Security 据此拦截登录
        return sysUser.getStatus() != null && sysUser.getStatus() == 1;
    }
}
