package com.aiworkspace.chat.entity;

import com.aiworkspace.common.core.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("chat_model")
public class ChatModel extends BaseEntity {

    private String modelName;
    private String provider;
    private String apiUrl;
    private String apiKey;
    private Integer enabled;
}
