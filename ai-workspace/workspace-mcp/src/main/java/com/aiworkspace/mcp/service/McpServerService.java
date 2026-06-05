package com.aiworkspace.mcp.service;

import com.aiworkspace.mcp.entity.McpServer;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface McpServerService extends IService<McpServer> {

    List<McpServer> listByUser(Long userId);

    McpServer getOwned(Long id, Long userId);

    void create(McpServer server, Long userId);

    void update(McpServer server, Long userId);

    void delete(Long id, Long userId);

    /** probe reachability of the server; for sse transport performs an HTTP check */
    String testConnectivity(Long id, Long userId);
}
