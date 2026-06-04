package com.aiworkspace.chat.service;

import com.aiworkspace.chat.entity.ChatMessage;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface ChatMessageService extends IService<ChatMessage> {

    List<ChatMessage> listBySessionId(Long sessionId);

    ChatMessage saveMessage(Long sessionId, String role, String content);
}
