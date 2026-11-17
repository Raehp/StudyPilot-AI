package com.studypilot.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class StudyPlanUpdateDTO {

    @Size(max = 64, message = "计划名称不能超过64个字符")
    private String title;

    @Size(max = 500, message = "学习目标不能超过500个字符")
    private String goal;

    @Size(max = 128, message = "学习主题不能超过128个字符")
    private String topic;

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

    /** 0-进行中 1-已完成 2-已放弃 */
    @Min(value = 0, message = "计划状态非法")
    @Max(value = 2, message = "计划状态非法")
    private Integer status;
}