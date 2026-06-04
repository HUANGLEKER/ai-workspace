package com.aiworkspace.system.service;

import com.aiworkspace.system.entity.SysUser;
import com.baomidou.mybatisplus.extension.service.IService;

public interface SysUserService extends IService<SysUser> {

    SysUser getByUsername(String username);
}
