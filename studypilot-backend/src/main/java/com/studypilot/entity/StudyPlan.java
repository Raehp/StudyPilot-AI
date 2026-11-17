package com.studypilot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("biz_study_plan")
public class StudyPlan {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long kbId;

    private String title;

    private String goal;

    private String topic;

    private LocalDate startDate;

    private LocalDate endDate;

    private Integer dailyMinutes;

    private String currentLevel;

    private String expectedOutcome;

    /** 0-进行中 1-已完成 2-已放弃 */
    private Integer status;

    /** 0-未生成 1-生成中 2-成功 3-失败 */
    private Integer generateStatus;

    private String generateError;

    private Integer progress;

    private String aiAdvice;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}