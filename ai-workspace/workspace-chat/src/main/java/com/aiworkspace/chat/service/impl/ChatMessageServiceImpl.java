package com.aiworkspace.chat.service.impl;

import com.aiworkspace.chat.entity.ChatMessage;
import com.aiworkspace.chat.mapper.ChatMessageMapper;
import com.aiworkspace.chat.service.ChatMessageService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.List;

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
    public ChatMessage saveMessage(Long sessionId, String role, String content) {
        ChatMessage message = new ChatMessage();
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content);
        save(message);
        return message;
    }
}
