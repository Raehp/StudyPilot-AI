package com.studypilot.vo;

import lombok.Data;

import java.time.LocalDate;

@Data
public class StudyTaskVO {

    private Long id;

    private Long planId;

    private String stage;

    private LocalDate taskDate;

    private String title;

    private String content;

    private String focus;

    private String recommendedContent;

    private Integer durationMin;

    private Boolean stageQuiz;

    /** 0-待办 1-已完成 */
    private Integer status;

    private Integer sortOrder;
}