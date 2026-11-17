package com.studypilot.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class StudyPlanCreateDTO {

    @NotBlank(message = "计划名称不能为空")
    @Size(max = 64, message = "计划名称不能超过64个字符")
    private String title;

    @Size(max = 500, message = "学习目标不能超过500个字符")
    private String goal;

    @Size(max = 128, message = "学习主题不能超过128个字符")
    private String topic;

    /** 关联知识库（0 或 null = 不结合文档） */
    private Long kbId;

    private LocalDate startDate;

    private LocalDate endDate;

    @Min(value = 5, message = "每天学习时长不能少于5分钟")
    @Max(value = 1440, message = "每天学习时长不能超过1440分钟")
    private Integer dailyMinutes;

    @Size(max = 32, message = "当前知识水平不能超过32个字符")
    private String currentLevel;

    @Size(max = 500, message = "预期学习成果不能超过500个字符")
    private String expectedOutcome;
}