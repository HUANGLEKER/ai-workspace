package com.aiworkspace.system.mapper;

import com.aiworkspace.system.entity.SysUser;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 系统用户 Mapper
 *
 * 继承 MyBatis Plus {@link BaseMapper} 提供单表 CRUD，
 * 并扩展跨表查询用户角色码的 RBAC 能力。
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    /**
     * 查询用户拥有的角色标识（role_code，形如 ROLE_ADMIN）。
     *
     * <p>RBAC：联表 {@code sys_role + sys_user_role} 取角色码，过滤已软删除角色，
     * 结果用于构建安全主体的权限集合。
     *
     * @param userId 用户 id
     * @return 角色码列表（含 ROLE_ 前缀）
     */
    @Select("SELECT r.role_code FROM sys_role r " +
            "JOIN sys_user_role ur ON ur.role_id = r.id " +
            "WHERE ur.user_id = #{userId} AND r.deleted = 0")
    List<String> selectRoleCodesByUserId(@Param("userId") Long userId);
}
