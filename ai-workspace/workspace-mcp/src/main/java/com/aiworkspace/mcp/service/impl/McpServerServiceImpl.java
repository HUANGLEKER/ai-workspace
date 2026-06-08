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

/**
 * MCP 服务器业务服务实现
 *
 * 主要职责：
 * 1. 用户私有 MCP 服务器的 CRUD
 * 2. 通过 getOwned 统一做归属校验，防止越权访问（IDOR）
 * 3. 针对 sse 传输的服务器提供 HTTP 连通性检测
 */
@Service
public class McpServerServiceImpl extends ServiceImpl<McpServerMapper, McpServer> implements McpServerService {

    @Override
    public List<McpServer> listByUser(Long userId) {
        // 按 createBy 过滤，仅返回当前用户拥有的 MCP 服务器
        return list(new LambdaQueryWrapper<McpServer>()
                .eq(McpServer::getCreateBy, userId)
                .orderByDesc(McpServer::getCreateTime));
    }

    @Override
    public McpServer getOwned(Long id, Long userId) {
        McpServer server = getById(id);
        if (server == null) throw new BusinessException("MCP服务器不存在");
        // 校验归属：非本人资源一律拒绝，防止 IDOR 越权
        if (!userId.equals(server.getCreateBy())) throw new BusinessException("无权操作该MCP服务器");
        return server;
    }

    @Override
    public void create(McpServer server, Long userId) {
        if (!StringUtils.hasText(server.getName())) {
            throw new BusinessException("名称不能为空");
        }
        // transport 默认为 sse，保持与前端默认值一致
        if (!StringUtils.hasText(server.getTransport())) {
            server.setTransport("sse");
        }
        server.setId(null);
        // 归属强制设为当前用户，忽略请求体可能携带的 createBy
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
        // 从已存在的行回填归属列，防止通过请求体篡改 createBy 转移归属
        server.setCreateBy(existing.getCreateBy());
        updateById(server);
    }

    @Override
    public void delete(Long id, Long userId) {
        // 删除前先做归属校验，越权直接抛异常
        getOwned(id, userId);
        removeById(id);
    }

    @Override
    public String testConnectivity(Long id, Long userId) {
        McpServer server = getOwned(id, userId);
        // stdio 传输无远程端点，无法通过 HTTP 探测，仅支持 sse 传输类型
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
            // 连接与读取超时各 3 秒，避免因网络延迟阻塞请求线程
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            int code = conn.getResponseCode();
            long latency = System.currentTimeMillis() - start;
            // 2xx/3xx/4xx 均表示端点可达，服务器返回 5xx 才认为不可达
            if (code >= 200 && code < 500) {
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
