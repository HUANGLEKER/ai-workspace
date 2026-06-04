package com.aiworkspace.system.security;

import com.aiworkspace.system.entity.SysUser;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

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

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // role_code already carries the ROLE_ prefix (e.g. ROLE_ADMIN), matching hasRole('ADMIN')
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

    @Override
    public boolean isEnabled() {
        return sysUser.getStatus() != null && sysUser.getStatus() == 1;
    }
}
