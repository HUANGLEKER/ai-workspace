package com.aiworkspace.prompt.service.impl;

import com.aiworkspace.common.exception.BusinessException;
import com.aiworkspace.prompt.entity.Prompt;
import com.aiworkspace.prompt.mapper.PromptMapper;
import com.aiworkspace.prompt.service.PromptService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class PromptServiceImpl extends ServiceImpl<PromptMapper, Prompt> implements PromptService {

    @Override
    public List<Prompt> listByUser(Long userId, String keyword, String category) {
        return list(new LambdaQueryWrapper<Prompt>()
                .eq(Prompt::getCreateBy, userId)
                .like(StringUtils.hasText(keyword), Prompt::getTitle, keyword)
                .eq(StringUtils.hasText(category), Prompt::getCategory, category)
                .orderByDesc(Prompt::getUpdateTime));
    }

    @Override
    public Prompt getOwned(Long id, Long userId) {
        Prompt prompt = getById(id);
        if (prompt == null) throw new BusinessException("提示词不存在");
        if (!userId.equals(prompt.getCreateBy())) throw new BusinessException("无权操作该提示词");
        return prompt;
    }

    @Override
    public void create(Prompt prompt, Long userId) {
        if (!StringUtils.hasText(prompt.getTitle())) {
            throw new BusinessException("标题不能为空");
        }
        if (!StringUtils.hasText(prompt.getContent())) {
            throw new BusinessException("内容不能为空");
        }
        prompt.setId(null);
        prompt.setCreateBy(userId);
        save(prompt);
    }

    @Override
    public void update(Prompt prompt, Long userId) {
        if (prompt.getId() == null) throw new BusinessException("提示词ID不能为空");
        Prompt existing = getOwned(prompt.getId(), userId);
        // prevent owner reassignment via request body
        prompt.setCreateBy(existing.getCreateBy());
        updateById(prompt);
    }

    @Override
    public void delete(Long id, Long userId) {
        getOwned(id, userId);
        removeById(id);
    }
}
