package com.aiworkspace.chat.entity;

import com.aiworkspace.common.core.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 聊天会话实体
 *
 * 映射 chat_session 表，表示一次完整的对话上下文。
 * 会话为用户私有资源，通过 userId 列做归属隔离，所有读写操作均需校验归属。
 *
 * @since 2026
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("chat_session")
public class ChatSession extends BaseEntity {

    /** 会话归属用户 ID —— 资源归属隔离列，防止越权访问他人会话（IDOR） */
    private Long userId;

    /** 会话标题 */
    private String title;

    /** 会话选用的模型名称，发送消息时按请求透传给 FastAPI */
    private String modelName;
}
