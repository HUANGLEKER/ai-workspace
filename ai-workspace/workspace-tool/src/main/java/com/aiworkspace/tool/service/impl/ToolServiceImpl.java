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

/**
 * 工具业务服务实现
 *
 * 主要职责：
 * 1. 用户私有工具的 CRUD
 * 2. 通过 getOwned 统一做归属校验，防止越权访问（IDOR）
 */
@Service
public class ToolServiceImpl extends ServiceImpl<ToolMapper, Tool> implements ToolService {

    @Override
    public List<Tool> listByUser(Long userId) {
        // 按 createBy 过滤，仅返回当前用户拥有的工具；Agent 运行时也通过此方法遍历可用工具
        return list(new LambdaQueryWrapper<Tool>()
                .eq(Tool::getCreateBy, userId)
                .orderByDesc(Tool::getCreateTime));
    }

    @Override
    public Tool getOwned(Long id, Long userId) {
        Tool tool = getById(id);
        if (tool == null) throw new BusinessException("工具不存在");
        // 校验归属：非本人资源一律拒绝，防止 IDOR 越权
        if (!userId.equals(tool.getCreateBy())) throw new BusinessException("无权操作该工具");
        return tool;
    }

    @Override
    public void create(Tool tool, Long userId) {
        if (!StringUtils.hasText(tool.getName())) {
            throw new BusinessException("工具名称不能为空");
        }
        // toolType 默认为 http，与前端默认值保持一致
        if (!StringUtils.hasText(tool.getToolType())) {
            tool.setToolType("http");
        }
        tool.setId(null);
        // 归属强制设为当前用户，忽略请求体可能携带的 createBy
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
        // 从已存在的行回填归属列，防止通过请求体篡改 createBy 转移归属
        tool.setCreateBy(existing.getCreateBy());
        updateById(tool);
    }

    @Override
    public void delete(Long id, Long userId) {
        // 删除前先做归属校验，越权直接抛异常
        getOwned(id, userId);
        removeById(id);
    }
}
