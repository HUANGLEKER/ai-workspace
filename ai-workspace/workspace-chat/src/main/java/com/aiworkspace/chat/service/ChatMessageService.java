package com.aiworkspace.chat.service;

import com.aiworkspace.chat.entity.ChatMessage;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 聊天消息服务接口
 *
 * 定义会话消息的查询、有界历史加载与持久化能力。
 * 调用方需在外层先完成会话归属校验，本服务不重复校验归属。
 */
public interface ChatMessageService extends IService<ChatMessage> {

    /**
     * 查询会话的全部消息（按创建时间升序）
     *
     * @param sessionId 会话 ID
     * @return 完整消息列表
     */
    List<ChatMessage> listBySessionId(Long sessionId);

    /**
     * 加载会话最近 {@code limit} 条消息，按时间正序（旧 → 新）返回。
     * 用于对发送给 LLM 的上下文做有界化，避免 token 成本与延迟随会话长度无限增长。
     *
     * @param sessionId 会话 ID
     * @param limit     上下文条数上限，<=0 时返回空列表
     * @return 时间正序的有界历史消息
     */
    List<ChatMessage> listRecentBySessionId(Long sessionId, int limit);

    /**
     * 保存一条消息
     *
     * @param sessionId 所属会话 ID
     * @param role      角色：user / assistant
     * @param content   消息内容
     * @return 持久化后的消息（含生成主键）
     */
    ChatMessage saveMessage(Long sessionId, String role, String content);
}
