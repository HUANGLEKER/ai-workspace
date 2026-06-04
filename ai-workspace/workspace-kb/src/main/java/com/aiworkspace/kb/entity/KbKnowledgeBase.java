package com.aiworkspace.kb.entity;

import com.aiworkspace.common.core.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("kb_knowledge_base")
public class KbKnowledgeBase extends BaseEntity {

    private String kbName;
    private String description;
    private Long createBy;
}
