package com.studypilot.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class QuizListItemVO {

    private Long id;

    private String title;

    private String topic;

    private Long kbId;

    private String kbName;

    private Integer difficulty;

    private Integer questionCount;

    /** 0-未作答 1-已提交 */
    private Integer status;

    private String generateStatus;

    private Integer score;

    private LocalDateTime createTime;
}