package com.aiworkspace.kb.entity;

import com.aiworkspace.common.core.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 知识库文档实体
 *
 * 映射 kb_document 表，记录上传到知识库的原始文件元信息及其向量化处理状态。
 * 文件本体存于 MinIO（filePath 为对象键），文档归属随所属知识库的 createBy 隔离。
 *
 * @author
 * @since 2026
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("kb_document")
public class KbDocument extends BaseEntity {

    /** 所属知识库 ID */
    private Long kbId;

    /** 原始文件名（用户上传时的名称） */
    private String fileName;

    /** MinIO 对象存储中的路径/对象键，格式 kb/{kbId}/{uuid}.{ext} */
    private String filePath;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 文件扩展名/类型，如 pdf、txt */
    private String fileType;

    /**
     * 文档向量化状态，由异步 embedding 管道驱动状态流转：
     * PENDING（待处理）→ PROCESSING（嵌入中）→ DONE（完成）/ FAILED（失败）
     */
    private String status;
}
