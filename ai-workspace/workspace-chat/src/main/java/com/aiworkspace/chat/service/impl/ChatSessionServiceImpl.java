package com.aiworkspace.chat.service.impl;

import com.aiworkspace.chat.entity.ChatSession;
import com.aiworkspace.chat.mapper.ChatSessionMapper;
import com.aiworkspace.chat.service.ChatSessionService;
import com.aiworkspace.common.exception.BusinessException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ChatSessionServiceImpl extends ServiceImpl<ChatSessionMapper, ChatSession>
        implements ChatSessionService {

    @Override
    public List<ChatSession> listByUserId(Long userId) {
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
        getOwned(id, userId);
        removeById(id);
    }

    @Override
    public ChatSession getOwned(Long id, Long userId) {
        ChatSession session = getById(id);
        if (session == null || !userId.equals(session.getUserId())) {
            throw new BusinessException("会话不存在或无权限");
        }
        return session;
    }
}
