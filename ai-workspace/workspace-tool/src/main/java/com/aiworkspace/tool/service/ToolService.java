package com.aiworkspace.tool.service;

import com.aiworkspace.tool.entity.Tool;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 工具业务服务接口
 *
 * 定义用户私有工具的增删改查能力，所有资源按 createBy 隔离；
 * listByUser 同时被 AgentController 用于运行时解析工具规格
 */
public interface ToolService extends IService<Tool> {

    /**
     * 查询当前用户的全部工具
     *
     * @param userId 当前用户ID
     * @return 按创建时间倒序排列的工具列表
     */
    List<Tool> listByUser(Long userId);

    /**
     * 获取指定工具并校验归属
     *
     * @param id     工具ID
     * @param userId 当前用户ID
     * @return 校验通过的工具
     * @throws com.aiworkspace.common.exception.BusinessException 不存在或非本人所有时抛出（防止 IDOR）
     */
    Tool getOwned(Long id, Long userId);

    /**
     * 创建工具
     *
     * @param tool   待创建的工具，归属强制设为当前用户
     * @param userId 当前用户ID
     */
    void create(Tool tool, Long userId);

    /**
     * 更新工具
     *
     * @param tool   待更新的工具
     * @param userId 当前用户ID
     */
    void update(Tool tool, Long userId);

    /**
     * 删除工具
     *
     * @param id     工具ID
     * @param userId 当前用户ID
     */
    void delete(Long id, Long userId);
}
