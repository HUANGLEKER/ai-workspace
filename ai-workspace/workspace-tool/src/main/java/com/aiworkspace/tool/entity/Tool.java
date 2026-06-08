package com.aiworkspace.tool.entity;

import com.aiworkspace.common.core.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 工具实体
 *
 * 对应 tool 表，表示用户私有的工具注册信息；通过 createBy 隔离归属。
 * Agent 运行时会按工具名查找本人已启用的工具，并将其解析为可执行的 HTTP tool 规格
 * POST 给 FastAPI 执行真实的工具调用。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tool")
public class Tool extends BaseEntity {

    /** 工具唯一名称，Agent 配置中通过此名称引用该工具 */
    private String name;

    private String description;

    /**
     * 工具类型
     *
     * http    —— 通过 HTTP 请求调用外部服务，endpoint + config 描述调用规格
     * builtin —— 平台内置工具，由 FastAPI 直接实现，无需外部 endpoint
     */
    private String toolType;

    /** HTTP tool 的调用端点 URL；仅 toolType=http 时有效 */
    private String endpoint;

    /**
     * 参数 schema 及调用配置的 JSON 字符串，格式如：
     * {"method":"POST","params":[{"name":"q","type":"string","description":"查询词","required":true}],"headers":{...}}
     * FastAPI 根据此 schema 构造 HTTP 请求并执行工具调用
     */
    private String config;

    /** 是否启用：1=启用，0=禁用；禁用的工具不会被 Agent 运行时加载 */
    private Integer enabled;

    /** 归属用户ID（资源隔离列），按 createBy 隔离避免越权访问 */
    private Long createBy;
}
