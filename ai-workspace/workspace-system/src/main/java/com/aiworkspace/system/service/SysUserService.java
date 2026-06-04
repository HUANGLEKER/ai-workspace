package com.aiworkspace.system.service;

import com.aiworkspace.system.entity.SysUser;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

public interface SysUserService extends IService<SysUser> {

    SysUser getByUsername(String username);

    Page<SysUser> pageUsers(int page, int size, String username);

    void addUser(SysUser user);

    void updateUser(SysUser user);

    void updateStatus(Long id, Integer status);
}
