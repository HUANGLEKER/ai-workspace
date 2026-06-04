package com.aiworkspace.system.security;

import com.aiworkspace.system.entity.SysUser;
import com.aiworkspace.system.mapper.SysUserMapper;
import com.aiworkspace.system.service.SysUserService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final SysUserService sysUserService;
    private final SysUserMapper sysUserMapper;

    public UserDetailsServiceImpl(SysUserService sysUserService, SysUserMapper sysUserMapper) {
        this.sysUserService = sysUserService;
        this.sysUserMapper = sysUserMapper;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        SysUser sysUser = sysUserService.getByUsername(username);
        if (sysUser == null) {
            throw new UsernameNotFoundException("用户不存在: " + username);
        }
        List<String> roles = sysUserMapper.selectRoleCodesByUserId(sysUser.getId());
        return new LoginUser(sysUser, roles);
    }
}
