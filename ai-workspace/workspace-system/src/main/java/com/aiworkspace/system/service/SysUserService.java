package com.aiworkspace.system.service;

import com.aiworkspace.system.entity.SysUser;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 系统用户服务接口
 *
 * 定义用户领域核心业务能力：账号查询、分页、增删改与状态管理。
 * 涉及密码 BCrypt 编码、用户名唯一性、密码哈希剥离等安全约束，具体见实现类。
 */
public interface SysUserService extends IService<SysUser> {

    /**
     * 按用户名查询用户（用于登录认证加载主体）
     *
     * @param username 用户名
     * @return 用户实体，不存在时返回 null
     */
    SysUser getByUsername(String username);

    /**
     * 分页查询用户（响应中已剥离密码哈希）
     *
     * @param page     页码
     * @param size     每页条数
     * @param username 用户名模糊查询条件，可为空
     * @return 用户分页结果
     */
    Page<SysUser> pageUsers(int page, int size, String username);

    /**
     * 新增用户（密码 BCrypt 编码后入库）
     *
     * @param user 用户信息
     * @throws com.aiworkspace.common.exception.BusinessException 用户名为空、密码为空或用户名已存在时抛出
     */
    void addUser(SysUser user);

    /**
     * 更新用户（用户名不可变；仅当传入新密码时才重新哈希）
     *
     * @param user 待更新用户信息
     * @throws com.aiworkspace.common.exception.BusinessException 用户 id 为空或用户不存在时抛出
     */
    void updateUser(SysUser user);

    /**
     * 更新用户启用/禁用状态
     *
     * @param id     用户 id
     * @param status 状态（0 禁用 / 1 正常）
     * @throws com.aiworkspace.common.exception.BusinessException 参数为空或用户不存在时抛出
     */
    void updateStatus(Long id, Integer status);
}
