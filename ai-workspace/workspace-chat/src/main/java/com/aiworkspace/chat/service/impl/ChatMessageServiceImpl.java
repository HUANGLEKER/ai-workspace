package com.aiworkspace.chat.service.impl;

import com.aiworkspace.chat.entity.ChatMessage;
import com.aiworkspace.chat.mapper.ChatMessageMapper;
import com.aiworkspace.chat.service.ChatMessageService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 聊天消息服务实现
 *
 * 主要职责：
 * 1. 会话消息的持久化与全量/有界历史查询
 * 2. 通过有界历史控制发送给 LLM 的上下文规模
 *
 * 归属隔离由调用方在外层（会话 getOwned 校验）保证，本类不重复校验。
 *
 * @since 2026
 */
@Service
public class ChatMessageServiceImpl extends ServiceImpl<ChatMessageMapper, ChatMessage>
        implements ChatMessageService {

    @Override
    public List<ChatMessage> listBySessionId(Long sessionId) {
        return list(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, sessionId)
                .orderByAsc(ChatMessage::getCreateTime));
    }

    @Override
    public List<ChatMessage> listRecentBySessionId(Long sessionId, int limit) {
        if (limit <= 0) {
            return new ArrayList<>();
        }
        // 先按时间倒序分页取最新 limit 条（仅扫描尾部数据，避免全量加载长会话），
        // 再反转恢复成时间正序，作为 LLM 的有界上下文
        Page<ChatMessage> page = page(
                new Page<>(1, limit),
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, sessionId)
                        .orderByDesc(ChatMessage::getCreateTime)
                        .orderByDesc(ChatMessage::getId));
        List<ChatMessage> records = page.getRecords();
        Collections.reverse(records);
        return records;
    }

    @Override
    public ChatMessage saveMessage(Long sessionId, String role, String content) {
        ChatMessage message = new ChatMessage();
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content);
        save(message);
        return message;
    }
}
