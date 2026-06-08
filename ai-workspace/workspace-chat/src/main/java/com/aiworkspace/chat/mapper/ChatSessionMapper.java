package com.aiworkspace.chat.mapper;

import com.aiworkspace.chat.entity.ChatSession;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 聊天会话 Mapper
 *
 * 基于 MyBatis Plus BaseMapper 提供 chat_session 表的通用 CRUD 能力。
 */
@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSession> {
}
