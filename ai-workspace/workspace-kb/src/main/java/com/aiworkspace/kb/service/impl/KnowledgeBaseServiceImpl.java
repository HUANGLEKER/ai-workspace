package com.aiworkspace.kb.service.impl;

import com.aiworkspace.common.exception.BusinessException;
import com.aiworkspace.kb.entity.KbKnowledgeBase;
import com.aiworkspace.kb.mapper.KbKnowledgeBaseMapper;
import com.aiworkspace.kb.service.KnowledgeBaseService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class KnowledgeBaseServiceImpl extends ServiceImpl<KbKnowledgeBaseMapper, KbKnowledgeBase>
        implements KnowledgeBaseService {

    @Override
    public List<KbKnowledgeBase> listByUser(Long userId) {
        return list(new LambdaQueryWrapper<KbKnowledgeBase>()
                .orderByDesc(KbKnowledgeBase::getCreateTime));
    }

    @Override
    public KbKnowledgeBase create(String kbName, String description, Long userId) {
        KbKnowledgeBase kb = new KbKnowledgeBase();
        kb.setKbName(kbName);
        kb.setDescription(description);
        kb.setCreateBy(userId);
        save(kb);
        return kb;
    }

    @Override
    public void update(KbKnowledgeBase kb, Long userId) {
        if (kb.getId() == null) throw new BusinessException("知识库ID不能为空");
        updateById(kb);
    }

    @Override
    public void delete(Long id, Long userId) {
        KbKnowledgeBase kb = getById(id);
        if (kb == null) throw new BusinessException("知识库不存在");
        removeById(id);
    }
}
