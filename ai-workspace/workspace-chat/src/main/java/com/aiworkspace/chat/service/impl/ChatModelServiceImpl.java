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

@Service
public class ChatModelServiceImpl extends ServiceImpl<ChatModelMapper, ChatModel>
        implements ChatModelService {

    @Override
    public List<ChatModel> listEnabled() {
        List<ChatModel> models = list(new LambdaQueryWrapper<ChatModel>()
                .eq(ChatModel::getEnabled, 1)
                .orderByAsc(ChatModel::getId));
        // Never expose API keys to the browser.
        models.forEach(m -> m.setApiKey(null));
        return models;
    }

    @Override
    public Page<ChatModel> pageModels(int page, int size, String modelName) {
        Page<ChatModel> p = page(new Page<>(page, size),
                new LambdaQueryWrapper<ChatModel>()
                        .like(StringUtils.hasText(modelName), ChatModel::getModelName, modelName)
                        .orderByDesc(ChatModel::getId));
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
        // The browser never receives the apiKey, so a blank value on update means
        // "keep the existing key" rather than "clear it".
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
