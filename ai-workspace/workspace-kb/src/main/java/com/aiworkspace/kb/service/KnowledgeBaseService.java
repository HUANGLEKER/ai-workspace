package com.aiworkspace.kb.service;

import com.aiworkspace.kb.entity.KbKnowledgeBase;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface KnowledgeBaseService extends IService<KbKnowledgeBase> {

    List<KbKnowledgeBase> listByUser(Long userId);

    KbKnowledgeBase create(String kbName, String description, Long userId);

    void update(KbKnowledgeBase kb, Long userId);

    void delete(Long id, Long userId);

    /**
     * Returns the knowledge base only if it exists and belongs to the given user;
     * otherwise throws a BusinessException. Used for resource-ownership checks.
     */
    KbKnowledgeBase getOwned(Long id, Long userId);
}
