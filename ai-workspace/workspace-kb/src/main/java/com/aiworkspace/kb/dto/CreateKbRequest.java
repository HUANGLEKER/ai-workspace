package com.aiworkspace.kb.dto;

import lombok.Data;

@Data
public class CreateKbRequest {
    private String kbName;
    private String description;
}
