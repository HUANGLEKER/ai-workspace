package com.aiworkspace.file.entity;

import com.aiworkspace.common.core.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 文件信息实体（对应表 file_info）
 *
 * 记录上传至 MinIO 的文件元数据。文件按 uploadBy 用户私有，
 * 所有 list/presign/删除路径必须按 uploadBy 过滤以隔离归属。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("file_info")
public class FileInfo extends BaseEntity {

    /** 原始文件名（用户上传时的文件名） */
    private String fileName;
    /** MinIO 对象存储路径（如 files/{uuid}.ext），与原始文件名解耦避免冲突 */
    private String filePath;
    private Long fileSize;
    /** 文件 MIME 类型 */
    private String fileType;
    /** 上传人用户 ID，作为文件归属隔离列（曾因未按此列隔离出现 IDOR 文件泄露） */
    private Long uploadBy;
}
