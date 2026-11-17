package com.studypilot.vo;

import lombok.Data;

@Data
public class StudyPlanProgressVO {

    private Long planId;

    private Integer total;

    private Integer completed;

    /** 0-100 */
    private Integer progress;

    /** 0-进行中 1-已完成 2-已放弃 */
    private Integer status;
}