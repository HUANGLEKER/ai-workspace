package com.aiworkspace.kb.mapper;

import com.aiworkspace.kb.entity.KbKnowledgeBase;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识库 Mapper
 *
 * 继承 MyBatis Plus BaseMapper，提供 kb_knowledge_base 表的基础 CRUD 能力。
 */
@Mapper
public interface KbKnowledgeBaseMapper extends BaseMapper<KbKnowledgeBase> {
}
