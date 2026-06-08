package com.aiworkspace.prompt.mapper;

import com.aiworkspace.prompt.entity.Prompt;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 提示词数据访问接口
 *
 * 继承 MyBatis Plus {@link BaseMapper}，提供 prompt 表的基础 CRUD 能力
 */
@Mapper
public interface PromptMapper extends BaseMapper<Prompt> {
}
