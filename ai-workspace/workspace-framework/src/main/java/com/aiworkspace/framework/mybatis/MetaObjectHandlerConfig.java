package com.aiworkspace.framework.mybatis;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * MyBatis Plus 字段自动填充处理器
 *
 * 在插入/更新时自动填充审计时间戳字段，免去业务代码手动设置，保证全表一致。
 *
 * 主要职责：
 * 1. 插入时填充 createTime 与 updateTime
 * 2. 更新时填充 updateTime
 *
 * 设计说明：仅对实体中标注了 @TableField(fill = ...) 的字段生效（见 BaseEntity）。
 * 个别只追加的表（如 sys_job_log）只有 create_time，故 updateFill 不会影响其已有行为。
 *
 * @author
 * @since 2026
 */
@Component
public class MetaObjectHandlerConfig implements MetaObjectHandler {

    /**
     * 插入时自动填充：创建时间与更新时间均设为当前时间
     */
    @Override
    public void insertFill(MetaObject metaObject) {
        // strictInsertFill 仅在字段存在且未被显式赋值时填充，避免覆盖业务已设置的值
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, LocalDateTime.now());
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }

    /**
     * 更新时自动填充：刷新更新时间为当前时间
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }
}
