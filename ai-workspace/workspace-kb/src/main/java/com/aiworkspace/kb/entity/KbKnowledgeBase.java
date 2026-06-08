package com.aiworkspace.kb.entity;

import com.aiworkspace.common.core.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 知识库实体
 *
 * 映射 kb_knowledge_base 表，是 RAG 文档与向量索引的逻辑归属单元。
 * 用户私有资源：通过 createBy 列隔离，所有 CRUD 均需经 getOwned 归属校验防止 IDOR。
 *
 * @author
 * @since 2026
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("kb_knowledge_base")
public class KbKnowledgeBase extends BaseEntity {

    /** 知识库名称 */
    private String kbName;

    /** 知识库描述 */
    private String description;

    /** 归属用户 ID（资源隔离列，校验调用方所有权以防越权访问） */
    private Long createBy;
}
