package com.aiworkspace.kb.mapper;

import com.aiworkspace.kb.entity.KbDocument;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识库文档 Mapper
 *
 * 继承 MyBatis Plus BaseMapper，提供 kb_document 表的基础 CRUD 能力。
 */
@Mapper
public interface KbDocumentMapper extends BaseMapper<KbDocument> {
}
