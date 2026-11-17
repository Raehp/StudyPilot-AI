package com.studypilot.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class QuizGenerateDTO {

    @NotNull(message = "请选择知识库")
    private Long kbId;

    @Size(max = 128, message = "知识主题不能超过128个字符")
    private String topic;

    @Size(max = 64, message = "测验名称不能超过64个字符")
    private String title;

    @Min(value = 1, message = "题目数量不能少于1")
    @Max(value = 20, message = "题目数量不能超过20")
    private Integer questionCount;

    /** 1-基础 2-进阶 3-挑战 */
    @Min(value = 1, message = "难度非法")
    @Max(value = 3, message = "难度非法")
    private Integer difficulty;

    /** 题型集合：1单选 2多选 3判断 4简答 */
    @NotEmpty(message = "请至少选择一种题型")
    private List<@Min(value = 1, message = "题型非法") @Max(value = 4, message = "题型非法") Integer> questionTypes;
}