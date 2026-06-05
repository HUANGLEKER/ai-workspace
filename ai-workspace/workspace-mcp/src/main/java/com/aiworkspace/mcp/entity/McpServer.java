package com.aiworkspace.mcp.entity;

import com.aiworkspace.common.core.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mcp_server")
public class McpServer extends BaseEntity {

    private String name;
    private String description;
    /** transport: sse / stdio */
    private String transport;
    /** base URL for sse transport (probed by the connectivity test) */
    private String url;
    /** command line for stdio transport */
    private String command;
    /** JSON config (headers, env, args) */
    private String config;
    private Integer enabled;
    private Long createBy;
}
