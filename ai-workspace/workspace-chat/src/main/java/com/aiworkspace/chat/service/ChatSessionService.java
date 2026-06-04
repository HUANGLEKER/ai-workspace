package com.aiworkspace.chat.service;

import com.aiworkspace.chat.entity.ChatSession;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface ChatSessionService extends IService<ChatSession> {

    List<ChatSession> listByUserId(Long userId);

    ChatSession createSession(Long userId, String title, String modelName);

    void deleteSession(Long id, Long userId);
}
