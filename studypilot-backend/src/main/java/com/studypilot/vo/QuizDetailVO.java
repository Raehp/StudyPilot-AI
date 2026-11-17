package com.studypilot.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 测验详情（练习态：题目不含答案与解析） */
@Data
public class QuizDetailVO {

    private Long id;

    private String title;

    private String topic;

    private Long kbId;

    private String kbName;

    private Integer difficulty;

    private List<Integer> questionTypes;

    private Integer questionCount;

    /** 0-未作答 1-已提交 */
    private Integer status;

    private String generateStatus;

    private String generateError;

    private Integer score;

    private LocalDateTime createTime;

    private List<QuizQuestionVO> questions;
}