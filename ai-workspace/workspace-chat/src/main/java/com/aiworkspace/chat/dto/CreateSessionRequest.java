package com.aiworkspace.chat.dto;

import lombok.Data;

/**
 * 创建会话请求参数
 *
 * 承载新建会话所需的标题与模型选择。
 */
@Data
public class CreateSessionRequest {

    /** 会话标题，为空时由服务端回退为默认标题 */
    private String title;

    /** 选用的模型名称 */
    private String modelName;
}
