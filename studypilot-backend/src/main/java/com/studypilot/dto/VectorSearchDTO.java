package com.studypilot.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class VectorSearchDTO {

    @NotBlank(message = "检索内容不能为空")
    private String query;

    /** 默认 5，上限 20 */
    private Integer topK;
}