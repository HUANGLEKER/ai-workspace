package com.aiworkspace.kb.service.impl;

import com.aiworkspace.common.exception.BusinessException;
import com.aiworkspace.kb.entity.KbKnowledgeBase;
import com.aiworkspace.kb.mapper.KbKnowledgeBaseMapper;
import com.aiworkspace.kb.service.KnowledgeBaseService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 知识库服务实现
 *
 * 基于 MyBatis Plus ServiceImpl，实现知识库的归属隔离 CRUD。
 * 所有变更操作均先经 getOwned 校验归属，防止越权（IDOR）。
 *
 * @author
 * @since 2026
 */
@Service
public class KnowledgeBaseServiceImpl extends ServiceImpl<KbKnowledgeBaseMapper, KbKnowledgeBase>
        implements KnowledgeBaseService {

    @Override
    public List<KbKnowledgeBase> listByUser(Long userId) {
        // 仅返回当前用户拥有（createBy）的知识库，按创建时间倒序
        return list(new LambdaQueryWrapper<KbKnowledgeBase>()
                .eq(KbKnowledgeBase::getCreateBy, userId)
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
        KbKnowledgeBase existing = getOwned(kb.getId(), userId);
        // 用已存在行的归属列回填，防止请求体篡改 createBy 实现越权改写
        kb.setCreateBy(existing.getCreateBy());
        updateById(kb);
    }

    @Override
    public void delete(Long id, Long userId) {
        // 删除前校验归属，避免删除他人知识库
        getOwned(id, userId);
        removeById(id);
    }

    @Override
    public KbKnowledgeBase getOwned(Long id, Long userId) {
        // 归属校验：不存在或归属不符均拒绝，统一防止 IDOR 越权
        KbKnowledgeBase kb = getById(id);
        if (kb == null) throw new BusinessException("知识库不存在");
        if (!userId.equals(kb.getCreateBy())) throw new BusinessException("无权操作该知识库");
        return kb;
    }
}
