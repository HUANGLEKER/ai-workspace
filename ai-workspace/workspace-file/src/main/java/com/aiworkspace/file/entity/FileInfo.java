package com.aiworkspace.file.entity;

import com.aiworkspace.common.core.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("file_info")
public class FileInfo extends BaseEntity {

    private String fileName;
    private String filePath;
    private Long fileSize;
    private String fileType;
    private Long uploadBy;
}
