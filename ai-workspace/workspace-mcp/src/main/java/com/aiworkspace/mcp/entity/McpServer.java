package com.aiworkspace.mcp.entity;

import com.aiworkspace.common.core.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * MCP 服务器实体
 *
 * 对应 mcp_server 表，表示用户私有的 MCP（Model Context Protocol）服务器注册信息；
 * 通过 createBy 隔离归属，支持 sse 与 stdio 两种传输方式。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mcp_server")
public class McpServer extends BaseEntity {

    private String name;
    private String description;

    /**
     * 传输协议类型
     *
     * sse   —— 基于 HTTP SSE，可在运行时通过 URL 远程加载工具（Agent 运行时支持）
     * stdio —— 基于标准输入输出，仅本地进程可用，运行时无法经 HTTP 加载
     */
    private String transport;

    /**
     * SSE 服务器的基础 URL；仅 transport=sse 时有效，连通性检测也针对此字段发起 HTTP 探测
     */
    private String url;

    /** stdio 传输的命令行启动指令；仅 transport=stdio 时有效 */
    private String command;

    /**
     * 扩展配置 JSON 字符串，格式如 {"headers":{...},"env":{...},"args":[...]}；
     * SSE 场景中的 headers 会在 Agent 运行时注入到请求头
     */
    private String config;

    /** 是否启用：1=启用，0=禁用；禁用的服务器不会被 Agent 运行时加载 */
    private Integer enabled;

    /** 归属用户ID（资源隔离列），按 createBy 隔离避免越权访问 */
    private Long createBy;
}
