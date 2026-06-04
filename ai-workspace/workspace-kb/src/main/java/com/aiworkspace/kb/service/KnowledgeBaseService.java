package com.aiworkspace.kb.service;

import com.aiworkspace.kb.entity.KbKnowledgeBase;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface KnowledgeBaseService extends IService<KbKnowledgeBase> {

    List<KbKnowledgeBase> listByUser(Long userId);

    KbKnowledgeBase create(String kbName, String description, Long userId);

    void update(KbKnowledgeBase kb, Long userId);

    void delete(Long id, Long userId);
}
