package com.aiworkspace.workflow.service.impl;

import com.aiworkspace.common.exception.BusinessException;
import com.aiworkspace.workflow.entity.Workflow;
import com.aiworkspace.workflow.mapper.WorkflowMapper;
import com.aiworkspace.workflow.service.WorkflowService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class WorkflowServiceImpl extends ServiceImpl<WorkflowMapper, Workflow> implements WorkflowService {

    @Override
    public List<Workflow> listByUser(Long userId) {
        return list(new LambdaQueryWrapper<Workflow>()
                .eq(Workflow::getCreateBy, userId)
                .orderByDesc(Workflow::getCreateTime));
    }

    @Override
    public Workflow getOwned(Long id, Long userId) {
        Workflow workflow = getById(id);
        if (workflow == null) throw new BusinessException("工作流不存在");
        if (!userId.equals(workflow.getCreateBy())) throw new BusinessException("无权操作该工作流");
        return workflow;
    }

    @Override
    public void create(Workflow workflow, Long userId) {
        if (!StringUtils.hasText(workflow.getName())) {
            throw new BusinessException("工作流名称不能为空");
        }
        workflow.setId(null);
        workflow.setCreateBy(userId);
        if (workflow.getEnabled() == null) {
            workflow.setEnabled(1);
        }
        save(workflow);
    }

    @Override
    public void update(Workflow workflow, Long userId) {
        if (workflow.getId() == null) throw new BusinessException("工作流ID不能为空");
        Workflow existing = getOwned(workflow.getId(), userId);
        // prevent owner reassignment via request body
        workflow.setCreateBy(existing.getCreateBy());
        updateById(workflow);
    }

    @Override
    public void delete(Long id, Long userId) {
        getOwned(id, userId);
        removeById(id);
    }
}
