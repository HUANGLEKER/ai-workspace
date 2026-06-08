package com.aiworkspace.chat.entity;

import com.aiworkspace.common.core.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 聊天消息实体
 *
 * 映射 chat_message 表，记录会话中的单条消息。
 * 自身不直接持有 userId，归属隔离通过所属会话（sessionId → ChatSession.userId）间接保证。
 *
 * @since 2026
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("chat_message")
public class ChatMessage extends BaseEntity {

    /** 所属会话 ID，归属校验通过该会话间接完成 */
    private Long sessionId;

    /** 消息角色：user（用户）/ assistant（模型回复） */
    private String role;

    /** 消息文本内容 */
    private String content;

    /** token 计数，用于统计与上下文成本核算 */
    private Integer tokenCount;
}
