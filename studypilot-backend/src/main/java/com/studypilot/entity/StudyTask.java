package com.studypilot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("biz_study_task")
public class StudyTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long planId;

    private Long userId;

    private String stage;

    private LocalDate taskDate;

    private String title;

    private String content;

    private String focus;

    private String recommendedContent;

    private Integer durationMin;

    /** 0-否 1-是 */
    private Integer stageQuiz;

    /** 0-待办 1-已完成 */
    private Integer status;

    private Integer sortOrder;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}