package com.studypilot.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 错题回顾（客观题答错 + 简答得分低于掌握阈值） */
@Data
public class WrongAnswerVO {

    private Long questionId;

    private Long quizId;

    private String quizTitle;

    private Integer type;

    private String stem;

    private List<String> options;

    private String answer;

    private String analysis;

    private String userAnswer;

    private Integer isCorrect;

    private Integer score;

    private String scoreBasis;

    private String knowledgePoint;

    private Long sourceDocId;

    private String sourceDocTitle;

    private String sourceContent;

    private LocalDateTime createTime;
}