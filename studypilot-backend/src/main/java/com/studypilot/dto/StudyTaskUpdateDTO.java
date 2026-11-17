package com.studypilot.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class StudyTaskUpdateDTO {

    @Size(max = 128, message = "任务名称不能超过128个字符")
    private String title;

    @Size(max = 1000, message = "任务详情不能超过1000个字符")
    private String content;

    @Size(max = 500, message = "学习重点不能超过500个字符")
    private String focus;

    @Size(max = 1000, message = "推荐学习内容不能超过1000个字符")
    private String recommendedContent;

    @Size(max = 64, message = "学习阶段不能超过64个字符")
    private String stage;

    private LocalDate taskDate;

    @Min(value = 0, message = "预计时长不能为负")
    @Max(value = 1440, message = "预计时长不能超过1440分钟")
    private Integer durationMin;

    @Min(value = 0, message = "阶段测验标记非法")
    @Max(value = 1, message = "阶段测验标记非法")
    private Integer stageQuiz;

    private Integer sortOrder;
}