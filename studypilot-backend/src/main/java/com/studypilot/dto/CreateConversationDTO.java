package com.studypilot.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateConversationDTO {

    @NotNull(message = "知识库 ID 不能为空")
    private Long kbId;

    @Size(max = 64, message = "会话标题最长 64 字符")
    private String title;
}