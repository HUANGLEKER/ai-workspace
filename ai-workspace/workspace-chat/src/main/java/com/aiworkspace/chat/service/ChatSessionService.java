package com.aiworkspace.chat.service;

import com.aiworkspace.chat.entity.ChatSession;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface ChatSessionService extends IService<ChatSession> {

    List<ChatSession> listByUserId(Long userId);

    ChatSession createSession(Long userId, String title, String modelName);

    void deleteSession(Long id, Long userId);

    /**
     * Returns the session only if it exists and belongs to the given user;
     * otherwise throws a BusinessException. Used for resource-ownership checks.
     */
    ChatSession getOwned(Long id, Long userId);
}
