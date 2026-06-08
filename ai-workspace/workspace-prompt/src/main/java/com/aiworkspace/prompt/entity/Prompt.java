package com.aiworkspace.prompt.entity;

import com.aiworkspace.common.core.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 提示词实体
 *
 * 对应 prompt 表，表示用户私有的提示词模板；通过 createBy 隔离归属，
 * 支持按标题关键字与分类检索。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("prompt")
public class Prompt extends BaseEntity {

    private String title;

    /** 提示词模板正文，可含占位符供用户填写后复用到 Chat */
    private String content;

    /** 自由分类标签，用于按场景分组（如"写作"、"编程"、"翻译"）；可为空 */
    private String category;

    private String description;

    /** 归属用户ID（资源隔离列），按 createBy 隔离避免越权访问 */
    private Long createBy;
}
