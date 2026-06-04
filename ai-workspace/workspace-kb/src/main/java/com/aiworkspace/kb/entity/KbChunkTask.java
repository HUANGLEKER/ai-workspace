package com.aiworkspace.kb.entity;

import com.aiworkspace.common.core.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("kb_chunk_task")
public class KbChunkTask extends BaseEntity {

    private Long documentId;
    /** PENDING / RUNNING / SUCCESS / FAILED */
    private String taskStatus;
    private String errorMsg;
}
