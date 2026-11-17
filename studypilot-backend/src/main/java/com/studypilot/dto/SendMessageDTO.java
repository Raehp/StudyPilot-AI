package com.studypilot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SendMessageDTO {

    @NotBlank(message = "问题不能为空")
    @Size(max = 2000, message = "问题最长 2000 字符")
    private String content;

    /** 可选覆盖默认 Top-K（1..20） */
    private Integer topK;

    /** 可选覆盖默认最低相似度阈值（0..1） */
    private Double minScore;
}