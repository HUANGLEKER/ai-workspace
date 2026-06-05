package com.aiworkspace.chat.service;

import com.aiworkspace.chat.entity.ChatModel;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface ChatModelService extends IService<ChatModel> {

    /**
     * Returns the enabled models for populating the chat model selector.
     * The {@code apiKey} field is stripped from every row before returning.
     */
    List<ChatModel> listEnabled();

    /**
     * Admin paginated list. {@code apiKey} is masked (never returned to the browser).
     */
    Page<ChatModel> pageModels(int page, int size, String modelName);

    void addModel(ChatModel model);

    void updateModel(ChatModel model);

    void updateStatus(Long id, Integer enabled);
}
