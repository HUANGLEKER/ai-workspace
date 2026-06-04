package com.aiworkspace.kb.entity;

import com.aiworkspace.common.core.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("kb_document")
public class KbDocument extends BaseEntity {

    private Long kbId;
    private String fileName;
    private String filePath;
    private Long fileSize;
    private String fileType;
    /** PENDING / PROCESSING / DONE / FAILED */
    private String status;
}
