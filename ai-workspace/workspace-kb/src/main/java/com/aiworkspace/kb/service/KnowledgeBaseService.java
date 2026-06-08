package com.aiworkspace.kb.service;

import com.aiworkspace.kb.entity.KbKnowledgeBase;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 知识库服务接口
 *
 * 定义知识库领域的核心业务能力，所有方法均按调用者 userId 做资源归属隔离。
 */
public interface KnowledgeBaseService extends IService<KbKnowledgeBase> {

    /**
     * 查询指定用户拥有的全部知识库（按创建时间倒序）
     *
     * @param userId 当前用户 ID（归属过滤列 createBy）
     * @return 该用户的知识库列表
     */
    List<KbKnowledgeBase> listByUser(Long userId);

    /**
     * 创建知识库，归属设为当前用户
     *
     * @param kbName      知识库名称
     * @param description 知识库描述
     * @param userId      归属用户 ID
     * @return 创建后的知识库（含生成的主键）
     */
    KbKnowledgeBase create(String kbName, String description, Long userId);

    /**
     * 更新知识库（先校验归属，禁止通过请求体篡改归属）
     *
     * @param kb     待更新的知识库
     * @param userId 当前用户 ID
     * @throws com.aiworkspace.common.exception.BusinessException ID 为空、不存在或无权操作时抛出
     */
    void update(KbKnowledgeBase kb, Long userId);

    /**
     * 删除知识库（先校验归属）
     *
     * @param id     知识库 ID
     * @param userId 当前用户 ID
     * @throws com.aiworkspace.common.exception.BusinessException 不存在或无权操作时抛出
     */
    void delete(Long id, Long userId);

    /**
     * 归属校验辅助方法：仅当知识库存在且属于该用户时返回，否则抛出 BusinessException。
     * 用于各变更/查询端点的资源归属校验，防止 IDOR 越权访问。
     *
     * @param id     知识库 ID
     * @param userId 当前用户 ID
     * @return 校验通过的知识库
     * @throws com.aiworkspace.common.exception.BusinessException 不存在或不属于该用户时抛出
     */
    KbKnowledgeBase getOwned(Long id, Long userId);
}
