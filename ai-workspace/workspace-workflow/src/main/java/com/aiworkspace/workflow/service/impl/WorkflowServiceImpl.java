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

/**
 * 工作流业务服务实现
 *
 * 主要职责：
 * 1. 用户私有工作流的 CRUD
 * 2. 通过 getOwned 统一做归属校验，防止越权访问（IDOR）
 */
@Service
public class WorkflowServiceImpl extends ServiceImpl<WorkflowMapper, Workflow> implements WorkflowService {

    @Override
    public List<Workflow> listByUser(Long userId) {
        // 按 createBy 过滤，仅返回当前用户拥有的工作流
        return list(new LambdaQueryWrapper<Workflow>()
                .eq(Workflow::getCreateBy, userId)
                .orderByDesc(Workflow::getCreateTime));
    }

    @Override
    public Workflow getOwned(Long id, Long userId) {
        Workflow workflow = getById(id);
        if (workflow == null) throw new BusinessException("工作流不存在");
        // 校验归属：非本人资源一律拒绝，防止 IDOR 越权
        if (!userId.equals(workflow.getCreateBy())) throw new BusinessException("无权操作该工作流");
        return workflow;
    }

    @Override
    public void create(Workflow workflow, Long userId) {
        if (!StringUtils.hasText(workflow.getName())) {
            throw new BusinessException("工作流名称不能为空");
        }
        workflow.setId(null);
        // 归属强制设为当前用户，忽略请求体可能携带的 createBy
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
        // 从已存在的行回填归属列，防止通过请求体篡改 createBy 转移归属
        workflow.setCreateBy(existing.getCreateBy());
        updateById(workflow);
    }

    @Override
    public void delete(Long id, Long userId) {
        // 删除前先做归属校验，越权直接抛异常
        getOwned(id, userId);
        removeById(id);
    }
}
