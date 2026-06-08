package com.aiworkspace.chat.service;

import com.aiworkspace.chat.entity.ChatSession;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 聊天会话服务接口
 *
 * 定义会话的查询、创建、删除及归属校验能力。
 * 会话为用户私有资源，所有操作均以 userId 做归属隔离。
 */
public interface ChatSessionService extends IService<ChatSession> {

    /**
     * 查询指定用户的全部会话
     *
     * @param userId 当前用户 ID（归属隔离条件）
     * @return 按创建时间倒序的会话列表
     */
    List<ChatSession> listByUserId(Long userId);

    /**
     * 创建新会话
     *
     * @param userId    归属用户 ID
     * @param title     会话标题，为空时回退为默认标题
     * @param modelName 选用模型名称
     * @return 持久化后的会话（含生成主键）
     */
    ChatSession createSession(Long userId, String title, String modelName);

    /**
     * 删除会话（先校验归属，防止越权删除他人会话）
     *
     * @param id     会话 ID
     * @param userId 当前用户 ID
     * @throws com.aiworkspace.common.exception.BusinessException 会话不存在或不属于该用户时抛出
     */
    void deleteSession(Long id, Long userId);

    /**
     * 归属校验：仅当会话存在且属于该用户时返回，否则抛出异常。
     * 所有读写会话消息的入口都应先调用此方法，防止越权访问（IDOR）。
     *
     * @param id     会话 ID
     * @param userId 当前用户 ID
     * @return 归属校验通过的会话
     * @throws com.aiworkspace.common.exception.BusinessException 会话不存在或无权限时抛出
     */
    ChatSession getOwned(Long id, Long userId);
}
