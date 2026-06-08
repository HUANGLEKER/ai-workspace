package com.aiworkspace.tool.mapper;

import com.aiworkspace.tool.entity.Tool;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工具数据访问接口
 *
 * 继承 MyBatis Plus {@link BaseMapper}，提供 tool 表的基础 CRUD 能力
 */
@Mapper
public interface ToolMapper extends BaseMapper<Tool> {
}
