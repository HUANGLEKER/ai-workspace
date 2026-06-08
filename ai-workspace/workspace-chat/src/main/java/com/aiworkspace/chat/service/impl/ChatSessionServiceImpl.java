package com.aiworkspace.chat.service.impl;

import com.aiworkspace.chat.entity.ChatSession;
import com.aiworkspace.chat.mapper.ChatSessionMapper;
import com.aiworkspace.chat.service.ChatSessionService;
import com.aiworkspace.common.exception.BusinessException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 聊天会话服务实现
 *
 * 主要职责：
 * 1. 会话的查询、创建与删除
 * 2. 通过 userId 做归属隔离，杜绝越权访问他人会话
 *
 * @since 2026
 */
@Service
public class ChatSessionServiceImpl extends ServiceImpl<ChatSessionMapper, ChatSession>
        implements ChatSessionService {

    @Override
    public List<ChatSession> listByUserId(Long userId) {
        // 按 userId 过滤实现归属隔离，仅返回当前用户自己的会话
        return list(new LambdaQueryWrapper<ChatSession>()
                .eq(ChatSession::getUserId, userId)
                .orderByDesc(ChatSession::getCreateTime));
    }

    @Override
    public ChatSession createSession(Long userId, String title, String modelName) {
        ChatSession session = new ChatSession();
        session.setUserId(userId);
        session.setTitle(title != null ? title : "新对话");
        session.setModelName(modelName);
        save(session);
        return session;
    }

    @Override
    public void deleteSession(Long id, Long userId) {
        // 删除前先做归属校验，防止越权删除他人会话
        getOwned(id, userId);
        removeById(id);
    }

    @Override
    public ChatSession getOwned(Long id, Long userId) {
        ChatSession session = getById(id);
        // 不存在或归属不匹配统一抛同一异常，避免泄露资源是否存在（防 IDOR 探测）
        if (session == null || !userId.equals(session.getUserId())) {
            throw new BusinessException("会话不存在或无权限");
        }
        return session;
    }
}
