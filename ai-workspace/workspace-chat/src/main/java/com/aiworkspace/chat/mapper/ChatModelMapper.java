package com.aiworkspace.chat.mapper;

import com.aiworkspace.chat.entity.ChatModel;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 聊天模型配置 Mapper
 *
 * 基于 MyBatis Plus BaseMapper 提供 chat_model 表的通用 CRUD 能力。
 */
@Mapper
public interface ChatModelMapper extends BaseMapper<ChatModel> {
}
