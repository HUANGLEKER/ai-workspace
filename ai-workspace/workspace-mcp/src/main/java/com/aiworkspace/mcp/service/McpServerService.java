package com.aiworkspace.mcp.service;

import com.aiworkspace.mcp.entity.McpServer;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * MCP 服务器业务服务接口
 *
 * 定义用户私有 MCP 服务器的增删改查及连通性检测能力，所有资源按 createBy 隔离
 */
public interface McpServerService extends IService<McpServer> {

    /**
     * 查询当前用户的全部 MCP 服务器
     *
     * @param userId 当前用户ID
     * @return 按创建时间倒序排列的 MCP 服务器列表
     */
    List<McpServer> listByUser(Long userId);

    /**
     * 获取指定 MCP 服务器并校验归属
     *
     * @param id     MCP 服务器ID
     * @param userId 当前用户ID
     * @return 校验通过的 MCP 服务器
     * @throws com.aiworkspace.common.exception.BusinessException 不存在或非本人所有时抛出（防止 IDOR）
     */
    McpServer getOwned(Long id, Long userId);

    /**
     * 创建 MCP 服务器
     *
     * @param server 待创建的 MCP 服务器，归属强制设为当前用户
     * @param userId 当前用户ID
     */
    void create(McpServer server, Long userId);

    /**
     * 更新 MCP 服务器
     *
     * @param server 待更新的 MCP 服务器
     * @param userId 当前用户ID
     */
    void update(McpServer server, Long userId);

    /**
     * 删除 MCP 服务器
     *
     * @param id     MCP 服务器ID
     * @param userId 当前用户ID
     */
    void delete(Long id, Long userId);

    /**
     * 检测 MCP 服务器连通性
     *
     * 仅支持 transport=sse 的服务器，通过 HTTP GET 探测 URL 是否可达，
     * 返回包含响应码与延迟的可读描述字符串（如"可达 (HTTP 200, 42ms)"）。
     *
     * @param id     MCP 服务器ID
     * @param userId 当前用户ID
     * @return 连通性描述字符串
     * @throws com.aiworkspace.common.exception.BusinessException transport 非 sse、URL 未配置或连接失败时抛出
     */
    String testConnectivity(Long id, Long userId);
}
