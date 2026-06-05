package com.aiworkspace.workflow.service;

import com.aiworkspace.workflow.entity.Workflow;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface WorkflowService extends IService<Workflow> {

    List<Workflow> listByUser(Long userId);

    Workflow getOwned(Long id, Long userId);

    void create(Workflow workflow, Long userId);

    void update(Workflow workflow, Long userId);

    void delete(Long id, Long userId);
}
