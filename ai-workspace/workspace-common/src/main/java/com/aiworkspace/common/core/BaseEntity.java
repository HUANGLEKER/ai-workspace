package com.aiworkspace.common.core;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 实体基类
 *
 * 抽取所有数据表共有的审计与主键字段，业务实体继承后即获得统一的
 * 主键自增、时间戳自动填充与逻辑删除能力。
 *
 * 设计说明：时间戳由 MetaObjectHandlerConfig 在插入/更新时自动填充；
 * deleted 配合 @TableLogic 实现软删除，查询自动追加未删除条件，物理数据得以保留。
 *
 * @author
 * @since 2026
 */
@Data
public class BaseEntity {

    /** 主键，数据库自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 创建时间，仅插入时自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间，插入与更新时均自动填充 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 逻辑删除标记（0-未删除，1-已删除），@TableLogic 使删除转为更新该标记 */
    @TableLogic
    private Integer deleted;
}
