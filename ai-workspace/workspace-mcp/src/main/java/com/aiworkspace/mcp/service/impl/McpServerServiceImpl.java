package com.aiworkspace.mcp.service.impl;

import com.aiworkspace.common.exception.BusinessException;
import com.aiworkspace.mcp.entity.McpServer;
import com.aiworkspace.mcp.mapper.McpServerMapper;
import com.aiworkspace.mcp.service.McpServerService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.HttpURLConnection;
import java.net.URI;
import java.util.List;

@Service
public class McpServerServiceImpl extends ServiceImpl<McpServerMapper, McpServer> implements McpServerService {

    @Override
    public List<McpServer> listByUser(Long userId) {
        return list(new LambdaQueryWrapper<McpServer>()
                .eq(McpServer::getCreateBy, userId)
                .orderByDesc(McpServer::getCreateTime));
    }

    @Override
    public McpServer getOwned(Long id, Long userId) {
        McpServer server = getById(id);
        if (server == null) throw new BusinessException("MCP服务器不存在");
        if (!userId.equals(server.getCreateBy())) throw new BusinessException("无权操作该MCP服务器");
        return server;
    }

    @Override
    public void create(McpServer server, Long userId) {
        if (!StringUtils.hasText(server.getName())) {
            throw new BusinessException("名称不能为空");
        }
        if (!StringUtils.hasText(server.getTransport())) {
            server.setTransport("sse");
        }
        server.setId(null);
        server.setCreateBy(userId);
        if (server.getEnabled() == null) {
            server.setEnabled(1);
        }
        save(server);
    }

    @Override
    public void update(McpServer server, Long userId) {
        if (server.getId() == null) throw new BusinessException("MCP服务器ID不能为空");
        McpServer existing = getOwned(server.getId(), userId);
        // prevent owner reassignment via request body
        server.setCreateBy(existing.getCreateBy());
        updateById(server);
    }

    @Override
    public void delete(Long id, Long userId) {
        getOwned(id, userId);
        removeById(id);
    }

    @Override
    public String testConnectivity(Long id, Long userId) {
        McpServer server = getOwned(id, userId);
        if (!"sse".equalsIgnoreCase(server.getTransport())) {
            throw new BusinessException("仅支持对 sse 传输的服务器进行连通性检测");
        }
        if (!StringUtils.hasText(server.getUrl())) {
            throw new BusinessException("该服务器未配置 URL");
        }
        HttpURLConnection conn = null;
        long start = System.currentTimeMillis();
        try {
            URI uri = URI.create(server.getUrl());
            conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            int code = conn.getResponseCode();
            long latency = System.currentTimeMillis() - start;
            if (code >= 200 && code < 500) {
                // any non-server-error response means the endpoint is reachable
                return "可达 (HTTP " + code + ", " + latency + "ms)";
            }
            throw new BusinessException("不可达: HTTP " + code);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("连接失败: " + e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}
