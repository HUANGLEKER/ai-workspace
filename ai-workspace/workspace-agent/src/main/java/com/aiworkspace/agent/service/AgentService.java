package com.aiworkspace.agent.service;

import com.aiworkspace.agent.entity.Agent;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface AgentService extends IService<Agent> {

    List<Agent> listByUser(Long userId);

    Agent getOwned(Long id, Long userId);

    void create(Agent agent, Long userId);

    void update(Agent agent, Long userId);

    void delete(Long id, Long userId);
}
