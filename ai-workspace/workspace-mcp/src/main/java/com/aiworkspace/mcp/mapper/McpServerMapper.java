package com.aiworkspace.mcp.mapper;

import com.aiworkspace.mcp.entity.McpServer;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * MCP 服务器数据访问接口
 *
 * 继承 MyBatis Plus {@link BaseMapper}，提供 mcp_server 表的基础 CRUD 能力
 */
@Mapper
public interface McpServerMapper extends BaseMapper<McpServer> {
}
