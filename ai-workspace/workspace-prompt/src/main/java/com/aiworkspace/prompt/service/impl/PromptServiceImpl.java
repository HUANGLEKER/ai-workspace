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

/**
 * 提示词业务服务实现
 *
 * 主要职责：
 * 1. 用户私有提示词的 CRUD，支持按标题/分类检索
 * 2. 通过 getOwned 统一做归属校验，防止越权访问（IDOR）
 */
@Service
public class PromptServiceImpl extends ServiceImpl<PromptMapper, Prompt> implements PromptService {

    @Override
    public List<Prompt> listByUser(Long userId, String keyword, String category) {
        // 按 createBy 过滤，关键字和分类均为可选条件，按更新时间降序以便快速找到最近编辑的提示词
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
        // 校验归属：非本人资源一律拒绝，防止 IDOR 越权
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
        // 归属强制设为当前用户，忽略请求体可能携带的 createBy
        prompt.setCreateBy(userId);
        save(prompt);
    }

    @Override
    public void update(Prompt prompt, Long userId) {
        if (prompt.getId() == null) throw new BusinessException("提示词ID不能为空");
        Prompt existing = getOwned(prompt.getId(), userId);
        // 从已存在的行回填归属列，防止通过请求体篡改 createBy 转移归属
        prompt.setCreateBy(existing.getCreateBy());
        updateById(prompt);
    }

    @Override
    public void delete(Long id, Long userId) {
        // 删除前先做归属校验，越权直接抛异常
        getOwned(id, userId);
        removeById(id);
    }
}
