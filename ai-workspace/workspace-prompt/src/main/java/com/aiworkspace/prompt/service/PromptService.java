package com.aiworkspace.prompt.service;

import com.aiworkspace.prompt.entity.Prompt;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 提示词业务服务接口
 *
 * 定义用户私有提示词的增删改查及按关键字/分类检索能力，所有资源按 createBy 隔离
 */
public interface PromptService extends IService<Prompt> {

    /**
     * 查询当前用户的提示词列表，支持按标题关键字和分类过滤
     *
     * @param userId   当前用户ID
     * @param keyword  标题模糊搜索关键字；为空则不过滤
     * @param category 分类精确匹配；为空则不过滤
     * @return 按更新时间倒序排列的提示词列表
     */
    List<Prompt> listByUser(Long userId, String keyword, String category);

    /**
     * 获取指定提示词并校验归属
     *
     * @param id     提示词ID
     * @param userId 当前用户ID
     * @return 校验通过的提示词
     * @throws com.aiworkspace.common.exception.BusinessException 不存在或非本人所有时抛出（防止 IDOR）
     */
    Prompt getOwned(Long id, Long userId);

    /**
     * 创建提示词
     *
     * @param prompt 待创建的提示词，归属强制设为当前用户
     * @param userId 当前用户ID
     */
    void create(Prompt prompt, Long userId);

    /**
     * 更新提示词
     *
     * @param prompt 待更新的提示词
     * @param userId 当前用户ID
     */
    void update(Prompt prompt, Long userId);

    /**
     * 删除提示词
     *
     * @param id     提示词ID
     * @param userId 当前用户ID
     */
    void delete(Long id, Long userId);
}
