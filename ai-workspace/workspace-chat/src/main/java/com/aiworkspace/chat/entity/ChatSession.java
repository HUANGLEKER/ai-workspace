package com.aiworkspace.chat.entity;

import com.aiworkspace.common.core.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("chat_session")
public class ChatSession extends BaseEntity {

    private Long userId;
    private String title;
    private String modelName;
}
