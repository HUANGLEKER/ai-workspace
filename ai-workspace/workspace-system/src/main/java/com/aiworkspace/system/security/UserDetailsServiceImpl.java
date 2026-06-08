package com.aiworkspace.system.security;

import com.aiworkspace.system.entity.SysUser;
import com.aiworkspace.system.mapper.SysUserMapper;
import com.aiworkspace.system.service.SysUserService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Spring Security 用户加载服务
 *
 * 实现 {@link UserDetailsService}，登录认证时按用户名加载用户主体，
 * 并装配其 RBAC 角色码，构建 {@link LoginUser} 交由框架完成密码比对。
 *
 * @since 2026
 */
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final SysUserService sysUserService;
    private final SysUserMapper sysUserMapper;

    public UserDetailsServiceImpl(SysUserService sysUserService, SysUserMapper sysUserMapper) {
        this.sysUserService = sysUserService;
        this.sysUserMapper = sysUserMapper;
    }

    /**
     * 按用户名加载用户主体（登录认证入口）
     *
     * @param username 登录用户名
     * @return 封装用户与角色的 {@link LoginUser} 主体
     * @throws UsernameNotFoundException 用户不存在时抛出
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        SysUser sysUser = sysUserService.getByUsername(username);
        if (sysUser == null) {
            throw new UsernameNotFoundException("用户不存在: " + username);
        }
        // RBAC：加载该用户的角色码（已带 ROLE_ 前缀），用于后续方法级权限校验
        List<String> roles = sysUserMapper.selectRoleCodesByUserId(sysUser.getId());
        return new LoginUser(sysUser, roles);
    }
}
