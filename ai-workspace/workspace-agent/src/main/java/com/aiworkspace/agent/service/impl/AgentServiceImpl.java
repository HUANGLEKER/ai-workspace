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

@Service
public class AgentServiceImpl extends ServiceImpl<AgentMapper, Agent> implements AgentService {

    @Override
    public List<Agent> listByUser(Long userId) {
        return list(new LambdaQueryWrapper<Agent>()
                .eq(Agent::getCreateBy, userId)
                .orderByDesc(Agent::getCreateTime));
    }

    @Override
    public Agent getOwned(Long id, Long userId) {
        Agent agent = getById(id);
        if (agent == null) throw new BusinessException("Agent不存在");
        if (!userId.equals(agent.getCreateBy())) throw new BusinessException("无权操作该Agent");
        return agent;
    }

    @Override
    public void create(Agent agent, Long userId) {
        if (!StringUtils.hasText(agent.getName())) {
            throw new BusinessException("Agent名称不能为空");
        }
        agent.setId(null);
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
        // prevent owner reassignment via request body
        agent.setCreateBy(existing.getCreateBy());
        updateById(agent);
    }

    @Override
    public void delete(Long id, Long userId) {
        getOwned(id, userId);
        removeById(id);
    }
}
