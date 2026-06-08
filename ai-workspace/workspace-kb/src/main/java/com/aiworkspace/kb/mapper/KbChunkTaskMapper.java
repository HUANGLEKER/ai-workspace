package com.aiworkspace.kb.mapper;

import com.aiworkspace.kb.entity.KbChunkTask;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文档切片/嵌入任务 Mapper
 *
 * 继承 MyBatis Plus BaseMapper，提供 kb_chunk_task 表的基础 CRUD 能力。
 */
@Mapper
public interface KbChunkTaskMapper extends BaseMapper<KbChunkTask> {
}
