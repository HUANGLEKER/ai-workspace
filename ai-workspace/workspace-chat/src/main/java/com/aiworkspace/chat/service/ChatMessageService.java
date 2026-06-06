package com.aiworkspace.chat.service;

import com.aiworkspace.chat.entity.ChatMessage;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface ChatMessageService extends IService<ChatMessage> {

    List<ChatMessage> listBySessionId(Long sessionId);

    /**
     * The most recent {@code limit} messages of a session in chronological order
     * (oldest → newest). Used to bound the context sent to the LLM so token cost
     * and latency don't grow unbounded with conversation length.
     */
    List<ChatMessage> listRecentBySessionId(Long sessionId, int limit);

    ChatMessage saveMessage(Long sessionId, String role, String content);
}
