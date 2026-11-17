package com.studypilot.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class StudyPlanVO {

    private Long id;

    private String title;

    private String goal;

    private String topic;

    private Long kbId;

    private String kbName;

    private LocalDate startDate;

    private LocalDate endDate;

    private Integer dailyMinutes;

    private String currentLevel;

    private String expectedOutcome;

    /** 0-进行中 1-已完成 2-已放弃 */
    private Integer status;

    /** NONE / GENERATING / SUCCESS / FAILED */
    private String generateStatus;

    private String generateError;

    private Integer progress;

    private String aiAdvice;

    private Integer taskCount;

    private Integer completedCount;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}