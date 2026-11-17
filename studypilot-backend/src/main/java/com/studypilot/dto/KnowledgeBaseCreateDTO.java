package com.studypilot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class KnowledgeBaseCreateDTO {

    @NotBlank(message = "知识库名称不能为空")
    @Size(max = 64, message = "知识库名称不能超过64个字符")
    private String name;

    @Size(max = 255, message = "描述不能超过255个字符")
    private String description;
}