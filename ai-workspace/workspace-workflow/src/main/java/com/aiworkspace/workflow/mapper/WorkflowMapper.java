package com.aiworkspace.workflow.mapper;

import com.aiworkspace.workflow.entity.Workflow;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工作流数据访问接口
 *
 * 继承 MyBatis Plus {@link BaseMapper}，提供 workflow 表的基础 CRUD 能力
 */
@Mapper
public interface WorkflowMapper extends BaseMapper<Workflow> {
}
