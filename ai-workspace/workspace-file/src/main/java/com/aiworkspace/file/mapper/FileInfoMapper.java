package com.aiworkspace.file.mapper;

import com.aiworkspace.file.entity.FileInfo;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文件信息 Mapper
 *
 * 基于 MyBatis Plus BaseMapper，提供 file_info 表的基础 CRUD 能力。
 */
@Mapper
public interface FileInfoMapper extends BaseMapper<FileInfo> {
}
