package com.aiworkspace.tool.service.impl;

import com.aiworkspace.common.exception.BusinessException;
import com.aiworkspace.tool.entity.Tool;
import com.aiworkspace.tool.mapper.ToolMapper;
import com.aiworkspace.tool.service.ToolService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class ToolServiceImpl extends ServiceImpl<ToolMapper, Tool> implements ToolService {

    @Override
    public List<Tool> listByUser(Long userId) {
        return list(new LambdaQueryWrapper<Tool>()
                .eq(Tool::getCreateBy, userId)
                .orderByDesc(Tool::getCreateTime));
    }

    @Override
    public Tool getOwned(Long id, Long userId) {
        Tool tool = getById(id);
        if (tool == null) throw new BusinessException("工具不存在");
        if (!userId.equals(tool.getCreateBy())) throw new BusinessException("无权操作该工具");
        return tool;
    }

    @Override
    public void create(Tool tool, Long userId) {
        if (!StringUtils.hasText(tool.getName())) {
            throw new BusinessException("工具名称不能为空");
        }
        if (!StringUtils.hasText(tool.getToolType())) {
            tool.setToolType("http");
        }
        tool.setId(null);
        tool.setCreateBy(userId);
        if (tool.getEnabled() == null) {
            tool.setEnabled(1);
        }
        save(tool);
    }

    @Override
    public void update(Tool tool, Long userId) {
        if (tool.getId() == null) throw new BusinessException("工具ID不能为空");
        Tool existing = getOwned(tool.getId(), userId);
        // prevent owner reassignment via request body
        tool.setCreateBy(existing.getCreateBy());
        updateById(tool);
    }

    @Override
    public void delete(Long id, Long userId) {
        getOwned(id, userId);
        removeById(id);
    }
}
