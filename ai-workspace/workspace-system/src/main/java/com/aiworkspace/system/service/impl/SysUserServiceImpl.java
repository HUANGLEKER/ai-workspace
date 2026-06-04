package com.aiworkspace.system.service.impl;

import com.aiworkspace.system.entity.SysUser;
import com.aiworkspace.system.mapper.SysUserMapper;
import com.aiworkspace.system.service.SysUserService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {

    @Override
    public SysUser getByUsername(String username) {
        return lambdaQuery().eq(SysUser::getUsername, username).one();
    }
}
