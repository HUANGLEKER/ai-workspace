package com.aiworkspace.chat.entity;

import com.aiworkspace.common.core.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 聊天模型配置实体
 *
 * 映射 chat_model 表，完全由 Spring Boot 拥有与管理（FastAPI 不读 MySQL）。
 * 选中的 modelName 在 chat 请求载荷中透传给 FastAPI，缺省时回退到 AI 服务的 LLM_MODEL。
 *
 * @since 2026
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("chat_model")
public class ChatModel extends BaseEntity {

    /** 模型名称，即透传给 FastAPI 的 model 字段 */
    private String modelName;

    /** 模型提供方（如 OpenAI、DeepSeek 等） */
    private String provider;

    /** 模型 API 地址 */
    private String apiUrl;

    /** 模型 API 密钥 —— 敏感字段，对外返回前必须置空脱敏 */
    private String apiKey;

    /** 是否启用：0-禁用，1-启用 */
    private Integer enabled;
}
