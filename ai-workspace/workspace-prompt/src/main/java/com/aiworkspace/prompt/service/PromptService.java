package com.aiworkspace.prompt.service;

import com.aiworkspace.prompt.entity.Prompt;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface PromptService extends IService<Prompt> {

    List<Prompt> listByUser(Long userId, String keyword, String category);

    Prompt getOwned(Long id, Long userId);

    void create(Prompt prompt, Long userId);

    void update(Prompt prompt, Long userId);

    void delete(Long id, Long userId);
}
