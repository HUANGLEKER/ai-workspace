package com.aiworkspace.chat.mapper;

import com.aiworkspace.chat.entity.ChatMessage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 聊天消息 Mapper
 *
 * 基于 MyBatis Plus BaseMapper 提供 chat_message 表的通用 CRUD 能力。
 */
@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {
}
