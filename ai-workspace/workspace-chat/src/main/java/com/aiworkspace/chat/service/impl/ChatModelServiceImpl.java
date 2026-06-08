package com.aiworkspace.chat.service.impl;

import com.aiworkspace.chat.entity.ChatModel;
import com.aiworkspace.chat.mapper.ChatModelMapper;
import com.aiworkspace.chat.service.ChatModelService;
import com.aiworkspace.common.exception.BusinessException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 聊天模型配置服务实现
 *
 * 主要职责：
 * 1. 模型配置的增删改查与启停
 * 2. apiKey 敏感字段的脱敏与"留空即保留原值"语义处理
 *
 * @since 2026
 */
@Service
public class ChatModelServiceImpl extends ServiceImpl<ChatModelMapper, ChatModel>
        implements ChatModelService {

    @Override
    public List<ChatModel> listEnabled() {
        List<ChatModel> models = list(new LambdaQueryWrapper<ChatModel>()
                .eq(ChatModel::getEnabled, 1)
                .orderByAsc(ChatModel::getId));
        // 安全：apiKey 绝不下发到浏览器，返回前统一脱敏
        models.forEach(m -> m.setApiKey(null));
        return models;
    }

    @Override
    public Page<ChatModel> pageModels(int page, int size, String modelName) {
        Page<ChatModel> p = page(new Page<>(page, size),
                new LambdaQueryWrapper<ChatModel>()
                        .like(StringUtils.hasText(modelName), ChatModel::getModelName, modelName)
                        .orderByDesc(ChatModel::getId));
        // 安全：分页结果同样脱敏 apiKey
        p.getRecords().forEach(m -> m.setApiKey(null));
        return p;
    }

    @Override
    public void addModel(ChatModel model) {
        if (!StringUtils.hasText(model.getModelName())) {
            throw new BusinessException("模型名称不能为空");
        }
        model.setId(null);
        if (model.getEnabled() == null) {
            model.setEnabled(1);
        }
        save(model);
    }

    @Override
    public void updateModel(ChatModel model) {
        ChatModel existing = getById(model.getId());
        if (existing == null) {
            throw new BusinessException("模型不存在");
        }
        // 因 apiKey 从不下发到浏览器，更新时传入空值表示"保留原密钥"，而非"清空"
        if (!StringUtils.hasText(model.getApiKey())) {
            model.setApiKey(existing.getApiKey());
        }
        updateById(model);
    }

    @Override
    public void updateStatus(Long id, Integer enabled) {
        ChatModel model = getById(id);
        if (model == null) {
            throw new BusinessException("模型不存在");
        }
        model.setEnabled(enabled);
        updateById(model);
    }
}
