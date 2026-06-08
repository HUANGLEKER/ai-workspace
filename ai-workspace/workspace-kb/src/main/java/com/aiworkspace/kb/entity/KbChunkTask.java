package com.aiworkspace.kb.entity;

import com.aiworkspace.common.core.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 文档切片/嵌入任务记录实体
 *
 * 映射 kb_chunk_task 表，为每次异步 embedding 构建留下一条审计/追踪记录，
 * 便于排查向量化失败原因（errorMsg）并展示处理进度。每个文档触发一次构建即新增一条任务。
 *
 * @author
 * @since 2026
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("kb_chunk_task")
public class KbChunkTask extends BaseEntity {

    /** 关联的文档 ID */
    private Long documentId;

    /**
     * 任务执行状态：
     * PENDING（待执行）/ RUNNING（执行中）/ SUCCESS（成功）/ FAILED（失败）
     */
    private String taskStatus;

    /** 失败时的错误信息，成功时为空 */
    private String errorMsg;
}
