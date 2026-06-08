package com.aiworkspace.chat.service;

import com.aiworkspace.chat.entity.ChatModel;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 聊天模型配置服务接口
 *
 * 定义模型配置的查询与管理能力。chat_model 表完全由 Spring Boot 管理，
 * apiKey 为敏感字段，对外返回前一律脱敏。
 */
public interface ChatModelService extends IService<ChatModel> {

    /**
     * 查询启用中的模型，用于前端模型选择器。
     * 返回前会剥离每一行的 apiKey，避免敏感信息泄露到浏览器。
     *
     * @return 启用模型列表（apiKey 已脱敏为 null）
     */
    List<ChatModel> listEnabled();

    /**
     * 管理端分页查询模型，apiKey 同样脱敏，不返回给浏览器。
     *
     * @param page      页码
     * @param size      每页条数
     * @param modelName 模型名称模糊匹配条件，可为空
     * @return 分页结果（apiKey 已脱敏）
     */
    Page<ChatModel> pageModels(int page, int size, String modelName);

    /**
     * 新增模型配置
     *
     * @param model 模型配置
     * @throws com.aiworkspace.common.exception.BusinessException 模型名称为空时抛出
     */
    void addModel(ChatModel model);

    /**
     * 更新模型配置
     *
     * @param model 模型配置
     * @throws com.aiworkspace.common.exception.BusinessException 模型不存在时抛出
     */
    void updateModel(ChatModel model);

    /**
     * 启用 / 禁用模型
     *
     * @param id      模型 ID
     * @param enabled 状态：0-禁用，1-启用
     * @throws com.aiworkspace.common.exception.BusinessException 模型不存在时抛出
     */
    void updateStatus(Long id, Integer enabled);
}
