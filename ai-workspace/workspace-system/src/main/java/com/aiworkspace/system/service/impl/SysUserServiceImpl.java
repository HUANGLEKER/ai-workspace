package com.aiworkspace.system.service.impl;

import com.aiworkspace.common.exception.BusinessException;
import com.aiworkspace.system.entity.SysUser;
import com.aiworkspace.system.mapper.SysUserMapper;
import com.aiworkspace.system.service.SysUserService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {

    private final PasswordEncoder passwordEncoder;

    public SysUserServiceImpl(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public SysUser getByUsername(String username) {
        return lambdaQuery().eq(SysUser::getUsername, username).one();
    }

    @Override
    public Page<SysUser> pageUsers(int page, int size, String username) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                .orderByDesc(SysUser::getCreateTime);
        if (StringUtils.hasText(username)) {
            wrapper.like(SysUser::getUsername, username);
        }
        Page<SysUser> result = page(new Page<>(page, size), wrapper);
        // never expose password hashes to the client
        result.getRecords().forEach(u -> u.setPassword(null));
        return result;
    }

    @Override
    public void addUser(SysUser user) {
        if (!StringUtils.hasText(user.getUsername())) {
            throw new BusinessException("用户名不能为空");
        }
        if (!StringUtils.hasText(user.getPassword())) {
            throw new BusinessException("密码不能为空");
        }
        if (getByUsername(user.getUsername()) != null) {
            throw new BusinessException("用户名已存在");
        }
        user.setId(null);
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        if (user.getStatus() == null) {
            user.setStatus(1);
        }
        save(user);
    }

    @Override
    public void updateUser(SysUser user) {
        if (user.getId() == null) {
            throw new BusinessException("用户ID不能为空");
        }
        SysUser existing = getById(user.getId());
        if (existing == null) {
            throw new BusinessException("用户不存在");
        }
        // username is immutable; ignore any change from the request body
        user.setUsername(existing.getUsername());
        // only re-hash when a new password is supplied, otherwise keep the existing one
        if (StringUtils.hasText(user.getPassword())) {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        } else {
            user.setPassword(null);
        }
        updateById(user);
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        if (id == null || status == null) {
            throw new BusinessException("参数不能为空");
        }
        SysUser existing = getById(id);
        if (existing == null) {
            throw new BusinessException("用户不存在");
        }
        update(new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, id)
                .set(SysUser::getStatus, status));
    }
}
