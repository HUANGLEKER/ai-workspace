package com.aiworkspace.tool.service;

import com.aiworkspace.tool.entity.Tool;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface ToolService extends IService<Tool> {

    List<Tool> listByUser(Long userId);

    Tool getOwned(Long id, Long userId);

    void create(Tool tool, Long userId);

    void update(Tool tool, Long userId);

    void delete(Long id, Long userId);
}
