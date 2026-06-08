package com.aiworkspace.system.entity;

import com.aiworkspace.common.core.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 系统用户实体
 *
 * 映射 {@code sys_user} 表，承载 RBAC 用户基础信息。
 * 继承 {@link BaseEntity}（含主键、软删除、自动填充时间戳）。
 *
 * 安全说明：{@code password} 存储 BCrypt 哈希，向前端返回前必须置空。
 *
 * @since 2026
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class SysUser extends BaseEntity {

    /** 登录用户名（唯一，创建后不可变） */
    private String username;

    /** 密码（BCrypt 哈希，禁止以明文存储或返回前端） */
    private String password;

    private String nickname;
    private String avatar;
    private String email;
    private String phone;

    /**
     * 账号状态
     *
     * 0-禁用
     * 1-正常
     */
    private Integer status;
}
