package com.aiworkspace.agent.service;

import com.aiworkspace.agent.entity.Agent;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * Agent 业务服务接口
 *
 * 定义用户私有 Agent 的增删改查能力，所有资源按 createBy 隔离
 */
public interface AgentService extends IService<Agent> {

    /**
     * 查询当前用户的全部 Agent
     *
     * @param userId 当前用户ID
     * @return 按创建时间倒序排列的 Agent 列表
     */
    List<Agent> listByUser(Long userId);

    /**
     * 获取指定 Agent 并校验归属
     *
     * @param id     Agent ID
     * @param userId 当前用户ID
     * @return 校验通过的 Agent
     * @throws com.aiworkspace.common.exception.BusinessException 不存在或非本人所有时抛出（防止 IDOR）
     */
    Agent getOwned(Long id, Long userId);

    /**
     * 创建 Agent
     *
     * @param agent  待创建的 Agent，归属强制设为当前用户
     * @param userId 当前用户ID
     */
    void create(Agent agent, Long userId);

    /**
     * 更新 Agent
     *
     * @param agent  待更新的 Agent
     * @param userId 当前用户ID
     */
    void update(Agent agent, Long userId);

    /**
     * 删除 Agent
     *
     * @param id     Agent ID
     * @param userId 当前用户ID
     */
    void delete(Long id, Long userId);
}
