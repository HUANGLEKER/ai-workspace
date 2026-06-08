package com.aiworkspace.workflow.service;

import com.aiworkspace.workflow.entity.Workflow;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 工作流业务服务接口
 *
 * 定义用户私有工作流的增删改查能力，所有资源按 createBy 隔离
 */
public interface WorkflowService extends IService<Workflow> {

    /**
     * 查询当前用户的全部工作流
     *
     * @param userId 当前用户ID
     * @return 按创建时间倒序排列的工作流列表
     */
    List<Workflow> listByUser(Long userId);

    /**
     * 获取指定工作流并校验归属
     *
     * @param id     工作流ID
     * @param userId 当前用户ID
     * @return 校验通过的工作流
     * @throws com.aiworkspace.common.exception.BusinessException 不存在或非本人所有时抛出（防止 IDOR）
     */
    Workflow getOwned(Long id, Long userId);

    /**
     * 创建工作流
     *
     * @param workflow 待创建的工作流，归属强制设为当前用户
     * @param userId   当前用户ID
     */
    void create(Workflow workflow, Long userId);

    /**
     * 更新工作流
     *
     * @param workflow 待更新的工作流
     * @param userId   当前用户ID
     */
    void update(Workflow workflow, Long userId);

    /**
     * 删除工作流
     *
     * @param id     工作流ID
     * @param userId 当前用户ID
     */
    void delete(Long id, Long userId);
}
