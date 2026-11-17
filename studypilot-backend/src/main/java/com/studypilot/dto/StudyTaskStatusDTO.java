package com.studypilot.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class StudyTaskStatusDTO {

    /** 0-待办 1-已完成 */
    @NotNull(message = "任务状态不能为空")
    @Min(value = 0, message = "任务状态非法")
    @Max(value = 1, message = "任务状态非法")
    private Integer status;
}