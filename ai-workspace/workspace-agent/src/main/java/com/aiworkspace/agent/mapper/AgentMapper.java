package com.aiworkspace.agent.mapper;

import com.aiworkspace.agent.entity.Agent;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * Agent 数据访问接口
 *
 * 继承 MyBatis Plus {@link BaseMapper}，提供 agent 表的基础 CRUD 能力
 */
@Mapper
public interface AgentMapper extends BaseMapper<Agent> {
}
