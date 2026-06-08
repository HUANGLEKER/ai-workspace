package com.aiworkspace.kb.dto;

import lombok.Data;

/**
 * 创建知识库请求 DTO
 *
 * 承载创建知识库时的入参；归属用户由当前登录上下文推导，不经请求体传入。
 */
@Data
public class CreateKbRequest {

    /** 知识库名称 */
    private String kbName;

    /** 知识库描述 */
    private String description;
}
