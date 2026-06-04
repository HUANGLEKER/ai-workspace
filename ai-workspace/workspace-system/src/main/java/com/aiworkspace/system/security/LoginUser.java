package com.aiworkspace.system.security;

import com.aiworkspace.system.entity.SysUser;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

public class LoginUser implements UserDetails {

    private final SysUser sysUser;

    public LoginUser(SysUser sysUser) {
        this.sysUser = sysUser;
    }

    public SysUser getSysUser() {
        return sysUser;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.emptyList();
    }

    @Override
    public String getPassword() {
        return sysUser.getPassword();
    }

    @Override
    public String getUsername() {
        return sysUser.getUsername();
    }

    @Override
    public boolean isEnabled() {
        return sysUser.getStatus() != null && sysUser.getStatus() == 1;
    }
}
