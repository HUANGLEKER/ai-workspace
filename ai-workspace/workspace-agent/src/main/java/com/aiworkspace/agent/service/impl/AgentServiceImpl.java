package com.aiworkspace.agent.service.impl;

import com.aiworkspace.agent.entity.Agent;
import com.aiworkspace.agent.mapper.AgentMapper;
import com.aiworkspace.agent.service.AgentService;
import com.aiworkspace.common.exception.BusinessException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Agent 业务服务实现
 *
 * 主要职责：
 * 1. 用户私有 Agent 的 CRUD
 * 2. 通过 getOwned 统一做归属校验，防止越权访问（IDOR）
 */
@Service
public class AgentServiceImpl extends ServiceImpl<AgentMapper, Agent> implements AgentService {

    @Override
    public List<Agent> listByUser(Long userId) {
        // 按 createBy 过滤，仅返回当前用户拥有的 Agent
        return list(new LambdaQueryWrapper<Agent>()
                .eq(Agent::getCreateBy, userId)
                .orderByDesc(Agent::getCreateTime));
    }

    @Override
    public Agent getOwned(Long id, Long userId) {
        Agent agent = getById(id);
        if (agent == null) throw new BusinessException("Agent不存在");
        // 校验归属：非本人资源一律拒绝，防止 IDOR 越权
        if (!userId.equals(agent.getCreateBy())) throw new BusinessException("无权操作该Agent");
        return agent;
    }

    @Override
    public void create(Agent agent, Long userId) {
        if (!StringUtils.hasText(agent.getName())) {
            throw new BusinessException("Agent名称不能为空");
        }
        agent.setId(null);
        // 归属强制设为当前用户，忽略请求体可能携带的 createBy
        agent.setCreateBy(userId);
        if (agent.getEnabled() == null) {
            agent.setEnabled(1);
        }
        save(agent);
    }

    @Override
    public void update(Agent agent, Long userId) {
        if (agent.getId() == null) throw new BusinessException("Agent ID不能为空");
        Agent existing = getOwned(agent.getId(), userId);
        // 从已存在的行回填归属列，防止通过请求体篡改 createBy 转移归属
        agent.setCreateBy(existing.getCreateBy());
        updateById(agent);
    }

    @Override
    public void delete(Long id, Long userId) {
        // 删除前先做归属校验，越权直接抛异常
        getOwned(id, userId);
        removeById(id);
    }
}
